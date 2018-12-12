package org.wildfly.extension.elytron;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.api.ContainerResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.clustering.controller.Operations;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.as.test.shared.SnapshotRestoreSetupTask;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;

import java.util.ArrayList;
import java.util.List;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ALLOW_RESOURCE_SERVICE_RESTART;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLBACK_ON_RUNTIME_FAILURE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;

/**
 * Test configures periodic rotating audit log and checks if the configuration persists in the configuration file.
 * Test for [ JBEAP-15472 ].
 *
 * @author Daniel Cihak
 */
@RunWith(Arquillian.class)
@ServerSetup(SnapshotRestoreSetupTask.class)
@RunAsClient
public class PeriodicRotatingAuditLogTestCase {

    private static final String DEPLOYMENT = "deployment";

    @ContainerResource
    private ManagementClient managementClient;

    @Deployment(name = DEPLOYMENT)
    public static WebArchive createDeployment() {
        WebArchive war = ShrinkWrap.create(WebArchive.class, DEPLOYMENT + ".war");
        war.addClass(PeriodicRotatingAuditLogTestCase.class);
        return war;
    }

    /**
     * Checks if the periodic rotating audit log configuration is persistent.
     *
     * @throws Exception
     */
    @Test
    public void testPeriodicRotatingConfigurationPersists() throws Exception {
        // /subsystem=elytron/file-audit-log=local-audit:remove
        ModelNode removeFileAuditLog = new ModelNode();
        removeFileAuditLog.get(OP).set(REMOVE);
        removeFileAuditLog.get(OP_ADDR).add(SUBSYSTEM, "elytron");
        removeFileAuditLog.get(OP_ADDR).add("file-audit-log", "local-audit");
        CoreUtils.applyUpdate(removeFileAuditLog, managementClient.getControllerClient());

        // /subsystem=elytron/periodic-rotating-file-audit-log=my_periodic_audit_log:add(path="my_periodic_audit.log",relative-to="jboss.server.log.dir",format=SIMPLE,synchronized=false,suffix=".yyyy-MM-dd-HH")
        List<ModelNode> operations = new ArrayList<>();
        ModelNode addFileAuditLog = createOpNode("subsystem=elytron/periodic-rotating-file-audit-log=my_periodic_audit_log", ADD);
        addFileAuditLog.get("path").set("my_periodic_audit.log");
        addFileAuditLog.get("relative-to").set("jboss.server.log.dir");
        addFileAuditLog.get("format").set("SIMPLE");
        addFileAuditLog.get("synchronized").set("false");
        addFileAuditLog.get("suffix").set(".yyyy-MM-dd-HH");
        operations.add(addFileAuditLog);

        ModelNode updateOp = Operations.createCompositeOperation(operations);
        updateOp.get(OPERATION_HEADERS, ROLLBACK_ON_RUNTIME_FAILURE).set(false);
        updateOp.get(OPERATION_HEADERS, ALLOW_RESOURCE_SERVICE_RESTART).set(true);
        CoreUtils.applyUpdate(updateOp, managementClient.getControllerClient());

        // /subsystem=elytron/periodic-rotating-file-audit-log=my_periodic_audit_log:read-resource
        PathAddress addr1 = PathAddress.pathAddress("subsystem", "elytron");
        addr1 = addr1.append("periodic-rotating-file-audit-log", "my_periodic_audit_log");
        ModelNode readResource1 = Util.createEmptyOperation("read-resource", addr1);
        ModelNode response1 = managementClient.getControllerClient().execute(readResource1);
        final String outcome1 = response1.get("outcome").asString();
        Assert.assertEquals("success", outcome1);

        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient(), 50000);

        // /subsystem=elytron/periodic-rotating-file-audit-log=my_periodic_audit_log:read-resource
        PathAddress addr = PathAddress.pathAddress("subsystem", "elytron");
        addr = addr.append("periodic-rotating-file-audit-log", "my_periodic_audit_log");
        ModelNode readResource = Util.createEmptyOperation("read-resource", addr);
        ModelNode response2 = managementClient.getControllerClient().execute(readResource);
        final String outcome2 = response2.get("outcome").asString();
        Assert.assertEquals("success", outcome2);
    }
}