# CSE 344 Homework 6: Database Transaction Management

**Objectives:**
To gain experience with database transaction management.
To learn how to use SQL from within Java via JDBC.

**Assignment tools:**
[Your HW5 code](https://gitlab.cs.washington.edu/cse344-au19/source/hw5/blob/master/hw5/hw5.md) and tools.

**Assigned date:** November 8, 2019

**Due date:** November 19, 2019, at 11:00 pm. Turn in your solution using `git`. You have 1.5 weeks for this assignment.

***Warning: This assignment doesn't need much code, but it can be tricky and time-consuming to debug.***

**What to turn in:**

Your code from HW5, copied to this homework's submission directory, with any changes needed for this assignment (including Query.java and createTables.sql). Your concurrency test files.

## Assignment Details

### General Setup
We maintain the same general specification as homework 5. Refer to the previous document for details. We have added some new test files, but your previous code should work as a starting point so you can copy it over.

### Database Design (10 points)
If your implementation for this lab homework with your old database, submit your previous createTables.sql file.

If you make any changes to your database schema and tables, submit a new copy of your createTables.sql file such that we can recreate your database starting from a copy of the flights database.

### Java Transactional Application (70 points)
Copy your submission directory from HW5 to your HW6 repo.

Now that your flight booking service is complete, you've noticed that there are problems when multiple users try to use your service concurrently. This is not a problem with the `search` command alone, because `search` is a read-only command, but it is important in the presence of booking commands that may conflict.
To resolve this challenge, you will need to implement transactions that ensure concurrent commands do not conflict.

As with the previous homework, you may use any classes from the [Java 8 standard JDK](https://docs.oracle.com/javase/8/docs/api/).

#### Transaction management

You must use SQL transactions to guarantee ACID properties: we have set the isolation level for your `Connection` , you must define begin- and end-transaction statements and insert them in appropriate places in `Query.java`. In particular, you must ensure that the following constraints are always satisfied, even if multiple instances of your application talk to the database at the same time.

*C1*. Each flight should have a maximum capacity that must not be exceeded. Each flight’s capacity is stored in the Flights table as in HW3, and you should have records as to how many seats remain on each flight based on the reservations.

*C2*. A customer may have at most one reservation on any given day, but they can be on more than 1 flight on the same day. (i.e., a customer can have one reservation on a given day that includes two flights, because the reservation is for a one-hop itinerary).

You must use transactions correctly such that race conditions introduced by concurrent execution cannot lead to an inconsistent state of the database. For example, multiple customers may try to book the same flight at the same time. Your properly designed transactions should prevent that.

Design transactions correctly. Avoid including user interaction inside a SQL transaction: that is, don't begin a transaction then wait for the user to decide what she wants to do (why?). The rule of thumb is that transactions need to be as short as possible, but not shorter.

Your `executeQuery` call will throw a `SQLException` when an error occurs (e.g., multiple customers try to book the same flight concurrently). Make sure you handle the `SQLException` appropriately. For instance, if a seat is still available, the booking should eventually go through (even though you might need to retry due to `SQLException`s being thrown). If no seat is available, the booking should be rolled back, etc.

When one uses a DBMS, recall that by default *each statement executes in its own transaction*. As discussed in lecture, to group multiple statements into a transaction, we use
```
BEGIN TRANSACTION
....
COMMIT or ROLLBACK
```
This is the same when executing transactions from Java, by default each SQL statement will be executed as its own transaction. To group multiple statements into one transaction in Java, you can do one of these approaches:

*Approach 1*:

Execute the SQL code for `BEGIN TRANSACTION` and friends directly, using the SQL code below (also check out SQL Azure's [transactions documentation](https://docs.microsoft.com/en-us/sql/t-sql/language-elements/transactions-transact-sql?view=sql-server-ver15):

```Java
private static final String BEGIN_TRANSACTION_SQL = "BEGIN TRANSACTION;";
protected PreparedStatement beginTransactionStatement;

private static final String COMMIT_SQL = "COMMIT TRANSACTION";
protected PreparedStatement commitTransactionStatement;

private static final String ROLLBACK_SQL = "ROLLBACK TRANSACTION";
protected PreparedStatement rollbackTransactionStatement;


// When you start the database up
Connection conn = [...]
conn.setAutoCommit(true); // This is the default setting, actually
conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

beginTransactionStatement = conn.prepareStatement(BEGIN_TRANSACTION_SQL);
commitTransactionStatement = conn.prepareStatement(COMMIT_SQL);
rollbackTransactionStatement = conn.prepareStatement(ROLLBACK_SQL);


// In each operation that is to be a multi-statement SQL transaction:
conn.setAutoCommit(false); 
// You MUST do this in order to tell JDBC that you are starting a 
// multi-statement transaction

beginTransactionStatement.executeUpdate();

// ... execute updates and queries.

commitTransactionStatement.executeUpdate();
// OR
rollbackTransactionStatement.executeUpdate();

conn.setAutoCommit(true);
// You MUST do this to make sure that future statements execute as their own transactions.
```

*Approach 2*:
```Java
// When you start the database up
Connection conn = [...]
conn.setAutoCommit(true); // This is the default setting, actually
conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

// In each operation that is to be a multi-statement SQL transaction:

conn.setAutoCommit(false);
// ... execute updates and queries.
conn.commit();
// OR
conn.rollback();
conn.setAutoCommit(true);
``` 

When auto-commit is set to true, each statement executes in its own transaction. With auto-commit set to false, you can execute many statements within a single transaction. By default, on any new connection to a DB auto-commit is set to true.

Again, the total amount of code for this assignment is small, but getting everything to work harmoniously may take some time. Debugging transactions can be a pain, but print statements are your friend!

## Transactional test cases (20 points)

In the previous homework we asked you to write test cases for the command that you implemented. In this homework, we ask you to write additional *parallel* test cases for each of the 7 commands.

In particular, we will now consider concurrent users interfacing with your database in test cases. The syntax for specifying concurrent test cases is specified below, after a reminder of the general test case format.

To run the JUnit test harness, execute in your HW5 submission directory:

```
mvn compile
mvn test
```

Copy the new test cases from the starter code and run this in your submission directory. You will see what the failed test cases print out. For every test case it will either print pass or fail, and for all failed cases it will dump out what the implementation returned, and you can compare it with the expected output in the corresponding case file. 

Each test case file is of the following format:

```sh
[command 1]
[command 2]
...
* 
[expected output line 1]
[expected output line 2]
...
*
# everything following ‘#’ is a comment on the same line
```

The `*` separates between commands and the expected output. To test with multiple concurrent users, simply add more `[command...] * [expected output...]` pairs to the file, for instance: 
 
 ```sh
 [command 1 for user1]
 [command 2 for user1]
 ...
 * 
 [expected output line 1 for user1]
 [expected output line 2 for user1]
 ...
 *
 [command 1 for user2]
 [command 2 for user2]
 ...
 * 
 [expected output line 1 for user2]
 [expected output line 2 for user2]
  ...
 *
 ```
 
Each user is expected to start concurrently in the beginning. If there are multiple output possibilities due to transactional behavior, then separate each group of expected output with `|`. See `book_2UsersSameFlight.txt` for an example.

Your task is to write at least 1 parallel test case (involving multiple application instances) for each of the 7 commands (you don't need to test quit). Separate each test case in its own file and name it <command name>_<some descriptive name for the test case>.txt and turn them in along with the original test cases. It’s fine to turn in test cases for erroneous conditions (e.g., booking on a full flight, paying the same reservation from two sessions, etc).

## Submission Instructions

Copy your HW5 code to your submission directory. Add your transactional code to `Query.java`. Adjust `create_tables.sql` if necessary. Add your test files to the cases directory.

**Important**: To remind you, in order for your answers to be added to the git repo, 
you need to explicitly add each file:

```sh
$ git add create_tables.sql ...
```

and push to make sure your code is uploaded to GitLab:

```sh
$ git commit
$ git push
```

**Again, just because your code has been committed on your local machine does not mean that it has been 
submitted -- it needs to be on GitLab!**

As with previous assignments, make sure you check the results afterwards to make sure that your files
have been committed.
