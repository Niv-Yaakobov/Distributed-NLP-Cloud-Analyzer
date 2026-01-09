Assignment 2: Collocation Extraction on AWS EMR
=================================================
Students:
1. Niv Yaakobov - 322578998
2. Rom Nissan - 209111533

-------------------------------------------------
Description:
-------------------------------------------------
This project extracts the top-100 collocations per decade from the Google N-Grams dataset using Hadoop MapReduce on AWS EMR. It calculates the Log-Likelihood Ratio (LLR) to determine the strength of the association between words.

-------------------------------------------------
How to Run:
-------------------------------------------------
1. Build the JAR:
   mvn clean package

2. Upload the JAR to S3 bucket.

3. Run the EmrRunner main class:
   mvn org.codehaus.mojo:exec-maven-plugin:3.1.0:java '-Dexec.mainClass=com.assignment2.EmrRunner'

   (Note: Ensure your AWS credentials are configured in ~/.aws/credentials)

-------------------------------------------------
Implementation Details (MapReduce Steps):
-------------------------------------------------
The solution is composed of 4 MapReduce steps:

Step 1: Calculate N
- Calculates the total number of unigrams (N) for each decade.
- Output: <Decade, Count>

Step 2: Join Bigrams with Unigram Counts (C1)
- Joins the 2-gram dataset with the 1-gram dataset to attach the count of the first word (C1).
- Optimization: We filtered Stop Words at this stage to drastically reduce the data size and prevent disk overflow errors.

Step 3: Join with Unigram Counts (C2) and Calculate LLR
- Joins the output of Step 2 with the 1-gram dataset again to attach the count of the second word (C2).
- Loads the "N" values (from Step 1) into memory (HashMap) during the setup phase.
- Calculates the LLR score using the formula.

Step 4: Sort and Filter
- Uses a Composite Key <Decade, LLR> and Secondary Sort.
- Sorts Decades in Ascending order.
- Sorts LLR in Ascending order (since the formula produces negative numbers for strong collocations, sorting from -100 to -1 keeps the strongest associations first).
- The Reducer limits output to the top 100 results per decade.

-------------------------------------------------
Challenges & Solutions:
-------------------------------------------------
1. Disk Space Error on AWS EMR:
   - Issue: The join operation in Step 2 generated too much intermediate data, causing the m4.large instances to run out of disk space ("No space left on device").
   - Solution: We implemented a StopWordsUtility to filter out unigrams and bigrams containing stop words (e.g., "the", "and") *before* they entered the shuffle phase. This significantly reduced the payload.

2. Sorting Logic:
   - Issue: The standard descending sort placed values near 0 (independent words) at the top, while strong collocations (large negative numbers) were at the bottom.
   - Solution: We implemented a custom WritableComparable key that sorts LLR values in ascending mathematical order (e.g., -100 before -0.5), ensuring the strongest associations appear first.

3. S3 File System Error:
   - Issue: Step 3 crashed with "Wrong FS" when trying to read the "N" file from S3 using the default HDFS configuration.
   - Solution: We updated the setup() method to dynamically load the FileSystem based on the file path (path.getFileSystem(conf)).

-------------------------------------------------
Analysis (English Dataset):
-------------------------------------------------
S3 link to the output file of the program:

not good collocations:
I shall
OF THE
I think
thou hast

good collocations:
starting point
private sector
Mental Health
vice versa
Harvard University

-------------------------------------------------
Analysis (Hebrew Dataset):
-------------------------------------------------
S3 link to the output file of the program:
s3://dist-sys-romniv-assign2/output/run_1765968615063/step4_final/part-r-00002
https://dist-sys-romniv-assign2.s3.us-east-1.amazonaws.com/output/run_1765968615063/step4_final/part-r-00002

not good collocations:
דף ודף
האלו נתונים
אנו רואים
אינו יכול
שאי אפשר

good collocations:
ארצות הברית
בית הספר
ראש הממשלה
עבודה זרה
שיר השירים


-------------------------------------------------
Statistics: Impact of Local Aggregation
-------------------------------------------------
We compared the network traffic (Shuffle phase) between Step 1 (which used a Combiner) and Step 2 (which could not use a Combiner due to the Join operation).

| Metric                         | With Aggregation (Step 1) | Without Aggregation (Step 2) |
|--------------------------------|---------------------------|------------------------------|
| Map Output Records             | 188,660,459               | 1,063,581,720                |
| Pairs Sent to Reducers         | 2,867                     | 1,063,581,720                |
| Data Size Sent (Shuffle Bytes) | ~31 KB                    | ~6.37 GB                     |
| Reduction Ratio                | ~99.99%                   | 0%                           |