package assignment.util;

import software.amazon.awssdk.regions.Region;

public class AwsConfig {

    public static final Region REGION = Region.US_EAST_1;

    public static final String S3_BUCKET = "dist-sys-romniv-assign1";

    public static final String QUEUE_APP_TO_MANAGER = "https://sqs.us-east-1.amazonaws.com/948266033069/dist-sys-romniv-app-to-manager";
    public static final String QUEUE_MANAGER_TO_APP = "https://sqs.us-east-1.amazonaws.com/948266033069/dist-sys-romniv-manager-to-app";
    public static final String QUEUE_MANAGER_TO_WORKER = "https://sqs.us-east-1.amazonaws.com/948266033069/dist-sys-romniv-manager-to-worker";
    public static final String QUEUE_WORKER_TO_MANAGER = "https://sqs.us-east-1.amazonaws.com/948266033069/dist-sys-romniv-worker-to-manager";

    public static final String MANAGER_AMI_ID = "ami-067cabbe2cf131618"; 
    public static final String WORKER_AMI_ID  = "ami-067cabbe2cf131618";  

     public static final String AMI_ID = "ami-067cabbe2cf131618";  
    public static final String KEY_NAME = "assign1key";           

    public static final String INSTANCE_TYPE = "t3.micro"; 
    public static final String SECURITY_GROUP_ID = "sg-0cc29b60df2a43dac";

    public static final String TAG_KEY_ROLE = "Role";
    public static final String TAG_VALUE_MANAGER = "Manager";
    public static final String TAG_VALUE_WORKER = "Worker";

    public static final String TAG_KEY_JOB_ID = "JobId";


}
