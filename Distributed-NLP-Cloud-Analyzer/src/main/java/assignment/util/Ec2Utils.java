package assignment.util;

import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Ec2Utils {

    private static final Ec2Client ec2 = Ec2Client.builder()
            .region(AwsConfig.REGION)
            .build();

    private static String encodeUserData(String script) {
        return Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_8));
    }


    
    /**
     * Ensure there is at least one Manager instance running or pending.
     * If none exists, launch one using MANAGER_AMI_ID and tag Role=Manager.
     */
    public static void ensureManagerRunning() {
        // Find existing manager instances in running/pending
        DescribeInstancesRequest req = DescribeInstancesRequest.builder()
                .filters(
                        Filter.builder()
                                .name("tag:" + AwsConfig.TAG_KEY_ROLE)
                                .values(AwsConfig.TAG_VALUE_MANAGER)
                                .build(),
                        Filter.builder()
                                .name("instance-state-name")
                                .values("pending", "running")
                                .build()
                )
                .build();

        DescribeInstancesResponse resp = ec2.describeInstances(req);

        boolean exists = resp.reservations().stream()
                .flatMap(r -> r.instances().stream())
                .findAny()
                .isPresent();

        if (exists) {
            System.out.println("Manager instance already running/pending.");
            return;
        }

        System.out.println("No Manager instance running. Launching a new one...");

        // This script is executed when the instance boots
        String userDataScript = "#!/bin/bash\n" +
                "ccd /opt/text-analysis\n" +
                "./run-manager.sh\n";

        String userDataBase64 = encodeUserData(userDataScript);

        
        RunInstancesRequest runReq = RunInstancesRequest.builder()
                .imageId(AwsConfig.MANAGER_AMI_ID)         // set in AwsConfig
                .instanceType(InstanceType.fromValue(AwsConfig.INSTANCE_TYPE))
                .minCount(1)
                .maxCount(1)
                .securityGroupIds(AwsConfig.SECURITY_GROUP_ID)
                .keyName(AwsConfig.KEY_NAME)
                .userData(userDataBase64)
                .tagSpecifications(
                        TagSpecification.builder()
                                .resourceType(ResourceType.INSTANCE)
                                .tags(
                                        Tag.builder()
                                                .key(AwsConfig.TAG_KEY_ROLE)
                                                .value(AwsConfig.TAG_VALUE_MANAGER)
                                                .build()
                                )
                                .build()
                )
                .build();

        RunInstancesResponse runResp = ec2.runInstances(runReq);
        String instanceId = runResp.instances().get(0).instanceId();
        System.out.println("Launched Manager instance: " + instanceId);
    }

    /**
     * Ensure that there are at least 'requiredWorkers' worker instances
     * (Role=Worker) in pending/running state, but never more than 19 in total.
     */
    public static void ensureWorkers(int requiredWorkers) {
        if (requiredWorkers <= 0) {
            System.out.println("ensureWorkers: requiredWorkers <= 0, nothing to do.");
            return;
        }

        // Find current workers: Role=Worker, state = pending/running
        DescribeInstancesRequest describeReq = DescribeInstancesRequest.builder()
                .filters(
                        Filter.builder()
                                .name("tag:" + AwsConfig.TAG_KEY_ROLE)
                                .values(AwsConfig.TAG_VALUE_WORKER)
                                .build(),
                        Filter.builder()
                                .name("instance-state-name")
                                .values("pending", "running")
                                .build()
                )
                .build();

        DescribeInstancesResponse describeResp = ec2.describeInstances(describeReq);

        int k = (int) describeResp.reservations().stream()
                .flatMap(r -> r.instances().stream())
                .count();

        System.out.println("ensureWorkers: required=" + requiredWorkers + ", current(k)=" + k);

        if (k >= requiredWorkers) {
            System.out.println("Already have enough workers, no need to launch more.");
            return;
        }

        int newWorkers = requiredWorkers - k;

        int maxAllowedNew = Math.max(0, 19 - k);
        if (maxAllowedNew <= 0) {
            System.out.println("Worker limit reached (19). Cannot launch more workers.");
            return;
        }

        if (newWorkers > maxAllowedNew) {
            System.out.println("Requested " + newWorkers + " new workers, but limited to "
                    + maxAllowedNew + " due to 19 cap.");
            newWorkers = maxAllowedNew;
        }

        if (newWorkers <= 0) {
            System.out.println("After applying cap, no new workers need to be launched.");
            return;
        }

        System.out.println("Launching " + newWorkers + " worker(s).");

        String workerAmiId = resolveWorkerAmiIdFromManager();

        String userDataScript = "#!/bin/bash\n" +
                "cd /opt/text-analysis\n" +
                "./run-worker.sh\n";

        String userDataBase64 = encodeUserData(userDataScript);

        RunInstancesRequest runReq = RunInstancesRequest.builder()
                .imageId(workerAmiId)      // use the manager's AMI
                .instanceType(InstanceType.fromValue(AwsConfig.INSTANCE_TYPE))
                .minCount(newWorkers)
                .maxCount(newWorkers)
                .securityGroupIds(AwsConfig.SECURITY_GROUP_ID)
                .keyName(AwsConfig.KEY_NAME)
                .userData(userDataBase64)
                .tagSpecifications(
                        TagSpecification.builder()
                                .resourceType(ResourceType.INSTANCE)
                                .tags(
                                        Tag.builder()
                                                .key(AwsConfig.TAG_KEY_ROLE)
                                                .value(AwsConfig.TAG_VALUE_WORKER)
                                                .build()
                                )
                                .build()
                )
                .build();


                RunInstancesResponse runResp = ec2.runInstances(runReq);
                List<String> ids = runResp.instances().stream()
                        .map(Instance::instanceId)
                        .collect(Collectors.toList());

                System.out.println("Launched worker instances: " + ids);
    }

    /**
     * Terminate ALL worker instances (Role=Worker),
     * in any of the listed states.
     * Used when terminateWhenDone=true on the last job.
     */
    public static void terminateAllWorkers() {
        System.out.println("Terminating all workers.");

        DescribeInstancesRequest describeReq = DescribeInstancesRequest.builder()
                .filters(
                        Filter.builder()
                                .name("tag:" + AwsConfig.TAG_KEY_ROLE)
                                .values(AwsConfig.TAG_VALUE_WORKER)
                                .build(),
                        Filter.builder()
                                .name("instance-state-name")
                                .values("pending", "running", "stopping", "stopped")
                                .build()
                )
                .build();

        DescribeInstancesResponse describeResp = ec2.describeInstances(describeReq);

        List<String> ids = describeResp.reservations().stream()
                .flatMap(r -> r.instances().stream())
                .map(Instance::instanceId)
                .collect(Collectors.toList());

        if (ids.isEmpty()) {
            System.out.println("No worker instances found.");
            return;
        }

        TerminateInstancesRequest termReq = TerminateInstancesRequest.builder()
                .instanceIds(ids)
                .build();

        ec2.terminateInstances(termReq);
        System.out.println("Terminate requested for worker instances: " + ids);
    }



        private static String resolveWorkerAmiIdFromManager() {
        DescribeInstancesRequest req = DescribeInstancesRequest.builder()
                .filters(
                        Filter.builder()
                                .name("tag:" + AwsConfig.TAG_KEY_ROLE)
                                .values(AwsConfig.TAG_VALUE_MANAGER)
                                .build(),
                        Filter.builder()
                                .name("instance-state-name")
                                .values("pending", "running")
                                .build()
                )
                .build();

        DescribeInstancesResponse resp = ec2.describeInstances(req);

        Optional<Instance> maybeManager = resp.reservations().stream()
                .flatMap(r -> r.instances().stream())
                .findFirst();

        if (maybeManager.isPresent()) {
                String amiId = maybeManager.get().imageId();
                System.out.println("Using manager AMI for workers: " + amiId);
                return amiId;
        }

        System.out.println("No running/pending manager found when resolving worker AMI; " +
                "falling back to AwsConfig.WORKER_AMI_ID (or MANAGER_AMI_ID).");

        if (AwsConfig.WORKER_AMI_ID != null && !AwsConfig.WORKER_AMI_ID.isEmpty()) {
                return AwsConfig.WORKER_AMI_ID;
        }
        return AwsConfig.MANAGER_AMI_ID;
        }


}
