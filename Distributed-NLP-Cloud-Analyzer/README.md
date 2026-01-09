README – manager-worker-stanford-parser

subbmited by:
Niv Yaakobov 322578998

1. Project Overview

This project implements a distributed text-analysis system on AWS.
The system receives input lines of the form <analysisType> \t <URL> and
performs POS, Constituency, or Dependency parsing using the Stanford
Parser.
The workload is distributed across EC2 Worker instances, orchestrated by
a Manager instance.
The Local Application acts as the entry point that uploads the input,
initiates the Manager, and receives the final HTML summary.

2. How to Run the Application

Start a new lab session.
Load the credentials into the local machine.
Launch a new instance using the latest AMI we created
(a new image still needs to be generated from the updated code — currently the JAR was copied manually).

Run aws configure and create a new image.
Update the AMI_ID in AwsConfig locally only.

Execute the application:
    java -jar manager-worker-stanford-parser.jar <inputFile> <outputFile> <n> [terminate]

if needed- 
    connect remotely to the server and run the script manually (./run_worker or ./run-manager)

Arguments

-   inputFile: Path to the local input list.
-   outputFile: Path to save the generated summary HTML.
-   n: Maximum number of URLs each worker should process.
-   terminate: Optional flag causing the Manager to shut down after
    finishing the job.

Prerequisites

-   Temporary AWS Academy credentials placed in:
    /home/ubuntu/.aws/credentials
-   S3 bucket and SQS queue names configured in AwsConfig.
-   AMI ID embedded in Manager code for worker launches.

3. AWS Environment Configuration

-   Region: us-east-1
-   S3 Bucket: dist-sys-romniv-assign1
-   SQS Queues:
    1.  Local → Manager
    2.  Manager → Local
    3.  Manager → Worker
    4.  Worker → Manager

4. EC2 and AMI Details

-   AMI ID: ami-067cabbe2cf131618
-   AMI OS: Ubuntu
-   Manager instance type: t3.micro
-   Worker instance type: Same as manager
-   AMI Contains:
    -   Java runtime
    -   AWS CLI
    -   Manager JAR
    -   Worker JAR
    -   run-manager.sh
    -   run-worker.sh
    -   logs/ folder
    -   AWS temporary credentials

5. System Architecture

Local Application

-   Uploads input file to S3
-   Sends “new job" message
-   Ensures Manager is active
-   Waits for “jobDone” message
-   Downloads summary HTML that contains URLs to the S3 Bucket
-   Sends termination request

Manager

-   Listens to Local→Manager SQS
-   Downloads input list
-   Creates task messages per URL (using threadPool)
-   Calculates required workers: requiredWorkers = ceil(totalMessages /
    n)
-   Ensures ≤19 workers
-   Uses thread pool for parallel job handling
-   Aggregates Worker results
-   Creates summary HTML
-   Sends job-complete message to the local application

Worker

-   Polls Manager→Worker queue
-   Downloads text file
-   Performs Stanford parsing
-   Uploads result to S3
-   Sends success or exception message
-   Infinite loop

6. Real Execution Parameters

-   Typical n: 3
-   Initial tests: 3 URLs
-   Reliable test: 1 URL
-   Processing time per URL: 5–10 min
-   Workers: dynamic, ≤19
-   Output HTML: created but inaccessible due to S3 permissions

7. Design Decisions

We used 4-Queue Architecture because:

-   Clear directional communication
-   Easier debugging
-   No race conditions
-   Clean asynchronous flow (mutability)


Visibility Timeout

-   Workers extend timeout if needed
-   Failed worker makes message visible again in the sqs so another worker will handle this task

Exception Handling

-   try/catch around risky operations
-   Worker reports failure instead of crashing
- using the visibility timeout we ensure that even if there is any failure in the worker, it still be good.


answers to the questions of the assignment (the last page):

Security:
No credentials are ever sent through SQS or stored in code. All AWS keys are loaded from the standard ~/.aws/credentials file on each machine.

Scalability:
The system scales because all communication is done through SQS and S3, which are fully managed and horizontally scalable. Heavy work is done by Workers, and the Manager only coordinates, so increasing the number of Workers allows the system to handle extremely large numbers of tasks and many clients.

Persistence & Failure Handling:
If a Worker dies or stalls, its tasks reappear after the visibility timeout and another Worker processes them. Manager tracks tasks by job ID and can continue even if messages arrive out of order. Outputs are stored in S3, so results persist across failures.

Threads:
The Manager uses a thread pool to process tasks for each job. Workers remain single-threaded because parsing is CPU-heavy, and scaling is achieved by launching additional Worker instances rather than adding threads inside one Worker.

Multiple Clients
Multiple Local Apps can run simultaneously because each job has a unique job ID and all communication goes through queues. Results remain isolated per job, and all clients complete independently.
Termination

When the Local App requests termination, the Manager finishes all ongoing jobs, terminates its Workers, and then shuts itself down.
Worker Utilization

All Workers actively pull tasks from a shared SQS queue. 
Manager Responsibilities

The Manager does only coordination: splitting jobs, launching Workers, forwarding tasks, collecting results, and building a summary. It does not do heavy parsing work.
Distributed Behavior

The system is fully asynchronous: no blocking RPC calls, no shared memory, and no node directly depends on another. 
All information flows through SQS/S3, so failures of one node do not block the rest.
