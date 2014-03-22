package org.opennms.netmgt.examples.snmp;

import static org.junit.Assert.*;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opennms.mock.snmp.MockSnmpAgent;
import org.opennms.netmgt.snmp.CollectionTracker;
import org.opennms.netmgt.snmp.SnmpAgentConfig;
import org.opennms.netmgt.snmp.SnmpObjId;
import org.opennms.netmgt.snmp.SnmpRowResult;
import org.opennms.netmgt.snmp.SnmpUtils;
import org.opennms.netmgt.snmp.SnmpWalker;
import org.opennms.netmgt.snmp.TableTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnmpWalkerTest  {
	
	private static final Logger LOG = LoggerFactory.getLogger(SnmpWalkerTest.class);

	MockSnmpAgent m_agent;
	
	@Before
	public void setUp() throws Exception {
	    
            // set up mock agent with walk data
            URL snmpWalk = new URL("file:src/test/resources/device.properties");
            m_agent = MockSnmpAgent.createAgentAndRun(snmpWalk, "127.0.0.1/9161");
            
	}
	
	@After
	public void tearDown() throws Exception {
	    m_agent.shutDownAndWait();
	}
	
	@Test(timeout=15000)
	public void test() throws Exception {
	    
	    // define oid for table
	    SnmpObjId tableID = SnmpObjId.get(".1.3.6.1.4.1.10728.2.1.1.4.1.1.1");
	    final SnmpObjId addrColumn = SnmpObjId.get(tableID, ".3");

	    final SnmpObjId[] columns = new SnmpObjId[] {
                    SnmpObjId.get(tableID, ".2"),
                    addrColumn,
                    SnmpObjId.get(tableID, ".4"),
                    SnmpObjId.get(tableID, ".5"),
                    SnmpObjId.get(tableID, ".6")
	    };

	    final StringBuilder buf = new StringBuilder();
	    
	    // create a tracker for walking the columns of that table
            CollectionTracker tracker = new TableTracker(columns) {

                    @Override
                    // this is called for each row of the table
                    public void rowCompleted(SnmpRowResult row) {
                        for(SnmpObjId col : columns) {
                            if (col.equals(addrColumn)) {
                                InetAddress addr;
                                try {
                                    addr = InetAddress.getByAddress(row.getValue(col).getBytes());
                                } catch (UnknownHostException e) {
                                    addr = null;
                                }
                                buf.append(String.format("%20s", addr));
                            } else {
                                buf.append(String.format("%20s", row.getValue(col).toDisplayString()));
                            }
                        }
                        buf.append("\n");
                    }

            };

            // set up credential for talking to the agent
            SnmpAgentConfig agentConfig = new SnmpAgentConfig(InetAddress.getByName("127.0.0.1"));
            agentConfig.setPort(9161);
            agentConfig.setReadCommunity("public");
            agentConfig.setVersion(SnmpAgentConfig.VERSION2C);


            // create a walker and pass in the the agent credentials and the tracker  
            SnmpWalker walker = SnmpUtils.createWalker(
                            agentConfig,
                            "Walking a table",
                            tracker
            );
            
            // start the walker
            walker.start();
            
            // wait for it to finish
            walker.waitFor();

            // ensure it didn't fail
            assertFalse(walker.timedOut());
            assertFalse(walker.failed());
            
            // print out the results accumulated in the rowCompleted method above
            System.err.println(buf);
	}
}
