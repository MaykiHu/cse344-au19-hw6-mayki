package edu.uw.cs;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.security.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import javax.xml.bind.*;

/**
 * Runs queries against a back-end database
 */
public class Query {
  // DB Connection
  private Connection conn;

  // Password hashing parameter constants
  private static final int HASH_STRENGTH = 65536;
  private static final int KEY_LENGTH = 128;

  // Canned queries
  private static final String CHECK_FLIGHT_CAPACITY = "SELECT capacity FROM Flights WHERE fid = ?";
  private PreparedStatement checkFlightCapacityStatement;
  // TODO: YOUR CODE HERE
  private PreparedStatement clearStatement;
  private PreparedStatement getUsersStatement;
  private PreparedStatement inUserStatement;
  private PreparedStatement getDirectStatement;
  private PreparedStatement getIndirectStatement;
  private PreparedStatement getf1CapacityStatement;
  private PreparedStatement getf2CapacityStatement;
  private PreparedStatement bookStatement;
  private PreparedStatement getIdStatement;
  private PreparedStatement getFlightStatement;
  private PreparedStatement getReservationsStatement;
  private PreparedStatement getUserStatement;
  private PreparedStatement updateBalanceStatement;
  private PreparedStatement getReservationStatement;
  private PreparedStatement updateReservationStatement;

  // Login and Reservation trackers
  private boolean isLogged;
  private String loggedUser;
  private List<FlightInfo> search = new ArrayList<FlightInfo>();

  /**
   * Establishes a new application-to-database connection. Uses the
   * dbconn.properties configuration settings
   *
   * @throws IOException
   * @throws SQLException
   */
  public void openConnection() throws IOException, SQLException {
    // Connect to the database with the provided connection configuration
    Properties configProps = new Properties();
    configProps.load(new FileInputStream("dbconn.properties"));
    String serverURL = configProps.getProperty("hw1.server_url");
    String dbName = configProps.getProperty("hw1.database_name");
    String adminName = configProps.getProperty("hw1.username");
    String password = configProps.getProperty("hw1.password");
    String connectionUrl = String.format("jdbc:sqlserver://%s:1433;databaseName=%s;user=%s;password=%s", serverURL,
        dbName, adminName, password);
    conn = DriverManager.getConnection(connectionUrl);

    // By default, automatically commit after each statement
    conn.setAutoCommit(true);

    // By default, set the transaction isolation level to serializable
    conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
  }

  /**
   * Closes the application-to-database connection
   */
  public void closeConnection() throws SQLException {
    conn.close();
  }

  /**
   * Clear the data in any custom tables created.
   *
   * WARNING! Do not drop any tables and do not clear the flights table.
   */
  public void clearTables() {
    try {
      // TODO: YOUR CODE HERE
      clearStatement.execute();
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  /*
   * prepare all the SQL statements in this method.
   */
  public void prepareStatements() throws SQLException {
    checkFlightCapacityStatement = conn.prepareStatement(CHECK_FLIGHT_CAPACITY);
    // TODO: YOUR CODE HERE
    clearStatement = conn.prepareStatement("DELETE FROM Users\nDELETE FROM Reservations");
    getUsersStatement = conn.prepareStatement("SELECT * FROM Users");
    getUserStatement = conn.prepareStatement("SELECT * FROM Users WHERE username = ?");
    updateBalanceStatement = conn.prepareStatement("UPDATE Users SET balance = ? WHERE username = ?");
    inUserStatement = conn.prepareStatement("INSERT INTO Users (username, password, salt, balance) VALUES (?, ?, ?, ?)");
    getDirectStatement = conn.prepareStatement("SELECT f.*" +
                                               "\nFROM Flights AS f" +
                                               "\nWHERE f.origin_city = ? AND f.dest_city = ? AND f.day_of_month = ? AND f.canceled = 0" +
                                               "\nORDER BY f.actual_time ASC, f.fid");
    getIndirectStatement = conn.prepareStatement("SELECT f1.*, f2.*,(f1.actual_time + f2.actual_time) AS totalTime, f1.fid AS fid1, f2.fid AS fid2, " +
                                                 "\nf1.day_of_month AS day_of_month1, f2.day_of_month AS day_of_month2, " +
                                                 "\nf1.carrier_id AS carrier_id1, f2.carrier_id AS carrier_id2, f1.flight_num AS flight_num1, " +
                                                 "\nf2.flight_num AS flight_num2, f1.origin_city AS origin_city1, f2.origin_city AS origin_city2, " +
                                                 "\nf1.dest_city AS dest_city1, f2.dest_city AS dest_city2, f1.actual_time AS actual_time1, " +
                                                 "\nf2.actual_time AS actual_time2, f1.capacity AS capacity1, f2.capacity AS capacity2, " +
                                                 "\nf1.price AS price1, f2.price AS price2" +
                                                 "\nFROM Flights AS f1, Flights AS f2" +
                                                 "\nWHERE f1.canceled = 0 AND f2.canceled = 0 AND f1.origin_city = ? AND f2.dest_city = ?" +
                                                     "\nAND f1.day_of_month = ? AND f1.day_of_month = f2.day_of_month" +
                                                     "\nAND f1.dest_city = f2.origin_city" +
                                                 "\nORDER BY totalTime ASC, f1.fid ASC, f2.fid ASC");
    getf1CapacityStatement = conn.prepareStatement("SELECT COUNT(fid1)\nFROM Reservations\nWHERE fid1 = ? AND cancelled = 0");
    getf2CapacityStatement = conn.prepareStatement("SELECT COUNT(fid2)\nFROM Reservations\nWHERE fid2 = ? AND cancelled = 0");
    bookStatement = conn.prepareStatement("INSERT INTO Reservations (rid, name, fid1, fid2, paid, cancelled)\nVALUES(?, ?, ?, ?, ?, ?)");
    getIdStatement = conn.prepareStatement("SELECT COUNT(*)\nFROM Reservations");
    getFlightStatement = conn.prepareStatement("SELECT f.*" +
                                               "\nFROM Flights AS f" +
                                               "\nWHERE f.fid = ?");
    getReservationsStatement = conn.prepareStatement("SELECT r.*" +
                                                     "\nFROM Reservations AS r" +
                                                     "\nWHERE r.name = ? AND r.cancelled = 0" +
                                                     "\nORDER BY r.rid");
    getReservationStatement = conn.prepareStatement("SELECT r.* FROM Reservations AS r WHERE r.rid = ? AND r.cancelled = 0");
    updateReservationStatement = conn.prepareStatement("UPDATE Reservations SET paid = ?, cancelled = ?  WHERE rid = ?");
  }

  /**
   * Takes a user's username and password and attempts to log the user in.
   *
   * @param username user's username
   * @param password user's password
   *
   * @return If someone has already logged in, then return "User already logged
   *         in\n" For all other errors, return "Login failed\n". Otherwise,
   *         return "Logged in as [username]\n".
   */
  public String transaction_login(String username, String password) {
    // TODO: YOUR CODE HERE
    if (isLogged && username != loggedUser) { // Logged in and not the logged user
        return "User already logged in\n";
    }
    try {
        ResultSet usersResults = getUsersStatement.executeQuery();
        while (usersResults.next()) {
          String name = usersResults.getString("username");
          if (name.equals(username)) {
              byte[] salt = usersResults.getBytes("salt");
              byte[] hash = usersResults.getBytes("password");
              byte[][] saltHash = getSaltHash(password, salt);
              if (Arrays.equals(hash, saltHash[1])) { // If passwords are equal
                  isLogged = true;
                  loggedUser = username;
                  return "Logged in as " + username + "\n";
              } else {
                  return "Login failed\n";
              }
          }
        }
        usersResults.close();
    } catch (SQLException e) {
        e.printStackTrace();
    }
    return "Login failed\n";
  }

  /**
   * Implement the create user function.
   *
   * @param username   new user's username. User names are unique the system.
   * @param password   new user's password.
   * @param initAmount initial amount to deposit into the user's account, should
   *                   be >= 0 (failure otherwise).
   *
   * @return either "Created user {@code username}\n" or "Failed to create user\n"
   *         if failed.
   */
  public String transaction_createCustomer(String username, String password, int initAmount) {
    // TODO: YOUR CODE HERE
    if (initAmount < 0) { // If invalid balance
        return "Failed to create user\n";
    }
    try {
        ResultSet usersResults = getUsersStatement.executeQuery();
        while (usersResults.next()) {
            String name = usersResults.getString("username");
            System.out.println(name);
            if (name.equals(username)) { // If invalid username
                usersResults.close();
                return "Failed to create user\n";
            }
        }
        byte[][] saltHash = getSaltHash(password, null);
        inUserStatement.setString(1, username);
        inUserStatement.setBytes(2, saltHash[1]);
        inUserStatement.setBytes(3, saltHash[0]);
        inUserStatement.setInt(4, initAmount);
        inUserStatement.execute();
        usersResults.close();
        return "Created user " + username + "\n";
    } catch (SQLException e) {
        return "Failed to create user\n";
    }
  }

  /*
   * This is from the template provided in hw5, with slight modifications
   * Creates a hashed passcode with a random salt
   * @param: password
   * returns: Byte[][] where Byte[0] is salt, Byte[1] is hash
   *
   */
  private byte[][] getSaltHash(String password, byte[] salt) {
      byte[][] saltHash;
      saltHash = new byte[2][];
      if (salt == null) { // If generating a new hash
          SecureRandom random = new SecureRandom();
          saltHash[0] = new byte[16];
          random.nextBytes(saltHash[0]);
      } else {
          saltHash[0] = salt;
      }
      // Specify the hash parameters
      KeySpec spec = new PBEKeySpec(password.toCharArray(), saltHash[0], HASH_STRENGTH, KEY_LENGTH);
      // Generate the hash
      SecretKeyFactory factory = null;
      try {
          factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
          saltHash[1] = factory.generateSecret(spec).getEncoded();
      } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
          throw new IllegalStateException();
      }
      return saltHash;
  }

   // For storing info on flights for itinerary-purposes
   private class FlightInfo implements Comparable<FlightInfo> {
       private int fid1;
       private int fid2;
       private int totalTime;
       private String flight;
       private int numFlights;

       // Constructs FlightInfo Object
       public FlightInfo(String flight, int totalTime, int fid1, int fid2) {
           this.fid1 = fid1;
           this.fid2 = fid2;
           this.totalTime = totalTime;
           this.flight = flight;
           numFlights = 1;
           if (fid2 != -1) {
               numFlights = 2;
           }
       }

       // Returns flight info
       public String getFlight() {
           return flight;
       }

       // Returns the fid, of corresponding plane as a param: 1 or 2
       public int getFid(int planeNum) {
           if (planeNum == 1) {
               return fid1;
           } else {
               return fid2;
           }
       }

       // Returns itinerary format of number of flights and flight time
       // Ex: # flight(s), # minutes\n
       public String getFlyAmt() {
           return ": " + numFlights + " flight(s), " + totalTime + " minutes\n";
       }

       public int compareTo(FlightInfo o) {
           if (this.totalTime == o.totalTime) {
               if (this.fid1 == o.fid1) {
                  return this.fid2 - o.fid2;
               } else {
                   return this.fid1 - o.fid1;
               }
           } else {
             return this.totalTime - o.totalTime;
           }
       }
   }
  /**
   * Implement the search function.
   *
   * Searches for flights from the given origin city to the given destination
   * city, on the given day of the month. If {@code directFlight} is true, it only
   * searches for direct flights, otherwise is searches for direct flights and
   * flights with two "hops." Only searches for up to the number of itineraries
   * given by {@code numberOfItineraries}.
   *
   * The results are sorted based on total flight time.
   *
   * @param originCity
   * @param destinationCity
   * @param directFlight        if true, then only search for direct flights,
   *                            otherwise include indirect flights as well
   * @param dayOfMonth
   * @param numberOfItineraries number of itineraries to return
   *
   * @return If no itineraries were found, return "No flights match your
   *         selection\n". If an error occurs, then return "Failed to search\n".
   *
   *         Otherwise, the sorted itineraries printed in the following format:
   *
   *         Itinerary [itinerary number]: [number of flights] flight(s), [total
   *         flight time] minutes\n [first flight in itinerary]\n ... [last flight
   *         in itinerary]\n
   *
   *         Each flight should be printed using the same format as in the
   *         {@code Flight} class. Itinerary numbers in each search should always
   *         start from 0 and increase by 1.
   *
   * @see Flight#toString()
   */
  public String transaction_search(String originCity, String destinationCity, boolean directFlight, int dayOfMonth,
      int numberOfItineraries) {
    // TODO: YOUR CODE HERE
    search.clear();
    StringBuffer itinerary = new StringBuffer();
    List<FlightInfo> flights = new ArrayList<FlightInfo>();
    int iNum = 0;
    String flight = "";
    try {
      getDirectStatement.setString(1, originCity);
      getDirectStatement.setString(2, destinationCity);
      getDirectStatement.setInt(3, dayOfMonth);
      ResultSet directResults = getDirectStatement.executeQuery();
      while (directResults.next() && iNum < numberOfItineraries) {
        int result_fid = directResults.getInt("fid");
        int result_dayOfMonth = directResults.getInt("day_of_month");
        String result_carrierId = directResults.getString("carrier_id");
        int result_flightNum = directResults.getInt("flight_num");
        String result_originCity = directResults.getString("origin_city");
        String result_destCity = directResults.getString("dest_city");
        int result_time = directResults.getInt("actual_time");
        int result_capacity = directResults.getInt("capacity");
        int result_price = directResults.getInt("price");
        flight = "ID: " + result_fid + " Day: " + result_dayOfMonth + " Carrier: " + result_carrierId + " Number: " + result_flightNum
            + " Origin: " + result_originCity + " Dest: " + result_destCity + " Duration: " + result_time
            + " Capacity: " + result_capacity + " Price: " + result_price + "\n";
        iNum++;
        flights.add(new FlightInfo(flight, result_time, result_fid, -1));
      }
      directResults.close();
    } catch (SQLException e) {
        return "Failed to search\n";
    }
    if (!directFlight) { // If indirect has to continue looking
        try {
            getIndirectStatement.setString(1, originCity);
            getIndirectStatement.setString(2, destinationCity);
            getIndirectStatement.setInt(3, dayOfMonth);
            ResultSet indirectResults = getIndirectStatement.executeQuery();
            while (indirectResults.next() && iNum < numberOfItineraries) {
                int[] result_fid = {indirectResults.getInt("fid1"), indirectResults.getInt("fid2")};
                int[] result_dayOfMonth = {indirectResults.getInt("day_of_month1"), indirectResults.getInt("day_of_month2")};
                String[] result_carrierId = {indirectResults.getString("carrier_id1"), indirectResults.getString("carrier_id2")};
                int[] result_flightNum = {indirectResults.getInt("flight_num1"), indirectResults.getInt("flight_num2")};
                String[] result_originCity = {indirectResults.getString("origin_city1"), indirectResults.getString("origin_city2")};
                String[] result_destCity = {indirectResults.getString("dest_city1"), indirectResults.getString("dest_city2")};
                int[] result_time = {indirectResults.getInt("actual_time1"), indirectResults.getInt("actual_time2")};
                int[] result_capacity = {indirectResults.getInt("capacity1"), indirectResults.getInt("capacity2")};
                int[] result_price = {indirectResults.getInt("price1"), indirectResults.getInt("price2")};
                int totalTime = result_time[0] + result_time[1];
                flight = "ID: " + result_fid[0] + " Day: " + result_dayOfMonth[0] + " Carrier: " + result_carrierId[0] + " Number: " + result_flightNum[0]
                    + " Origin: " + result_originCity[0] + " Dest: " + result_destCity[0] + " Duration: " + result_time[0]
                    + " Capacity: " + result_capacity[0] + " Price: " + result_price[0] + "\n";
                flight += "ID: " + result_fid[1] + " Day: " + result_dayOfMonth[1] + " Carrier: " + result_carrierId[1] + " Number: " + result_flightNum[1]
                    + " Origin: " + result_originCity[1] + " Dest: " + result_destCity[1] + " Duration: " + result_time[1]
                    + " Capacity: " + result_capacity[1] + " Price: " + result_price[1] + "\n";
                iNum++;
                flights.add(new FlightInfo(flight, totalTime, result_fid[0], result_fid[1]));
            }
            indirectResults.close();
        } catch (SQLException e) {
            return "Failed to search\n";
        }
    }
    if (flights.isEmpty()) {
        return "No flights match your selection\n";
    }
    Collections.sort(flights);
    for (int i = 0; i < flights.size(); i++) {
        FlightInfo flightInfo = flights.get(i);
        itinerary.append("Itinerary " + i + flightInfo.getFlyAmt() + flightInfo.getFlight());
        if (isLogged) { // Only count this as possible booking search
            search.add(flightInfo);
        }
    }
    return itinerary.toString();
  }

  /**
   * Implements the book itinerary function.
   *
   * @param itineraryId ID of the itinerary to book. This must be one that is
   *                    returned by search in the current session.
   *
   * @return If the user is not logged in, then return "Cannot book reservations,
   *         not logged in\n". If try to book an itinerary with invalid ID, then
   *         return "No such itinerary {@code itineraryId}\n". If the user already
   *         has a reservation on the same day as the one that they are trying to
   *         book now, then return "You cannot book two flights in the same
   *         day\n". For all other errors, return "Booking failed\n".
   *
   *         And if booking succeeded, return "Booked flight(s), reservation ID:
   *         [reservationId]\n" where reservationId is a unique number in the
   *         reservation system that starts from 1 and increments by 1 each time a
   *         successful reservation is made by any user in the system.
   */
  public String transaction_book(int itineraryId) {
    // TODO: YOUR CODE HERE
    if (!isLogged) {
        return "Cannot book reservations, not logged in\n";
    } else if (itineraryId < 0 || search.isEmpty() || itineraryId >= search.size()) {
        return "No such itinerary " + itineraryId + "\n";
    } else { // Can possibly make a booking
        FlightInfo flightInfo = search.get(itineraryId);
        int fid1 = flightInfo.getFid(1);
        int fid2 = flightInfo.getFid(2);
        try {
            getReservationsStatement.setString(1, loggedUser);
            ResultSet resResults = getReservationsStatement.executeQuery();
            while (resResults.next()) {
                int resFid1 = resResults.getInt("fid1");
                getFlightStatement.setInt(1, resFid1);
                ResultSet flightResult = getFlightStatement.executeQuery();
                flightResult.next();
                int reserve_dayOfMonth = flightResult.getInt("day_of_month");
                flightResult.close();
                getFlightStatement.setInt(1, fid1);
                flightResult = getFlightStatement.executeQuery();
                flightResult.next();
                int itinerary_dayOfMonth = flightResult.getInt("day_of_month");
                if (itinerary_dayOfMonth == reserve_dayOfMonth) {
                    return "You cannot book two flights in the same day\n";
                }
            }
            int f1TotCap = checkFlightCapacity(fid1);
            int f2TotCap = 0;
            getf1CapacityStatement.setInt(1, fid1);
            getf2CapacityStatement.setInt(1, fid1);
            ResultSet getf1CurCap = getf1CapacityStatement.executeQuery();
            ResultSet getf2CurCap = getf2CapacityStatement.executeQuery();
            getf1CurCap.next();
            getf2CurCap.next();
            int f1CurCap = getf1CurCap.getInt(1) + getf2CurCap.getInt(1);
            getf1CurCap.close();
            getf2CurCap.close();
            int f2CurCap = 0;
            if (fid2 != -1) { // There is a fid2
                f2TotCap = checkFlightCapacity(fid2);
                getf1CapacityStatement.setInt(1, fid2);
                getf2CapacityStatement.setInt(1, fid2);
                getf1CurCap = getf1CapacityStatement.executeQuery();
                getf2CurCap = getf2CapacityStatement.executeQuery();
                getf1CurCap.next();
                getf2CurCap.next();
                f2CurCap = getf1CurCap.getInt(1) + getf2CurCap.getInt(1);
                getf1CurCap.close();
                getf2CurCap.close();
            }
            if (f1CurCap > f1TotCap) { // Will exceed capacity flight 1
                return "Booking failed\n";
            } else if (fid2 != -1 && f2CurCap > f2TotCap) { // Will exceed capacity flight 2
                return "Booking failed\n";
            }
            int id = getId();
            bookStatement.setInt(1, id);
            bookStatement.setString(2, loggedUser);
            bookStatement.setInt(3, fid1);
            bookStatement.setInt(5, 0);
            bookStatement.setInt(6, 0);
            if (fid2 == -1) { // If no flight 2
                bookStatement.setNull(4, java.sql.Types.INTEGER);
            } else {
                bookStatement.setInt(4, fid2);
            }
            bookStatement.execute();
            bookStatement.close();
            return "Booked flight(s), reservation ID: " + id + "\n";
        } catch (SQLException e) {
            return "Booking failed\n";
        }
    }
  }

  /**
   * Implements the pay function.
   *
   * @param reservationId the reservation to pay for.
   *
   * @return If no user has logged in, then return "Cannot pay, not logged in\n"
   *         If the reservation is not found / not under the logged in user's
   *         name, then return "Cannot find unpaid reservation [reservationId]
   *         under user: [username]\n" If the user does not have enough money in
   *         their account, then return "User has only [balance] in account but
   *         itinerary costs [cost]\n" For all other errors, return "Failed to pay
   *         for reservation [reservationId]\n"
   *
   *         If successful, return "Paid reservation: [reservationId] remaining
   *         balance: [balance]\n" where [balance] is the remaining balance in the
   *         user's account.
   */
  public String transaction_pay(int reservationId) {
    // TODO: YOUR CODE HERE
    if (!isLogged) { // Not logged in
        return "Cannot pay, not logged in\n";
    } else {
        try {
            getReservationStatement.setInt(1, reservationId);
            ResultSet resResult = getReservationStatement.executeQuery();
            if (resResult.next()) {
                String username = resResult.getString("name");
                if (username.equals(loggedUser)) { // Matched user to reservation
                    int paid = resResult.getInt("paid");
                    if (paid == 1) {
                        return "Cannot find unpaid reservation " + reservationId +
                        " under user: " + loggedUser + "\n";
                    } else { // Not paid
                        int fid1 = resResult.getInt("fid1");
                        int fid2 = resResult.getInt("fid2");
                        if (updateBalance(reservationId, -1)) { // Able to pay, so pays in updateBalance
                            return "Paid reservation: " + reservationId + " remaining balance: " +
                                   getBalance() + "\n";
                        } else { // Not able to pay
                            return "User has only " + getBalance() +
                                   " in account but itinerary costs " +
                                   (getPrice(fid1) + getPrice(fid2)) + "\n";
                        }
                    }
                }
            }
            resResult.close();
            return "Cannot find unpaid reservation " + reservationId +
                   " under user: " + loggedUser + "\n";
        } catch (SQLException e) {
            return "Failed to pay for reservation " + reservationId +"\n";
        }
    }
  }

  /**
   * Implements the reservations function.
   *
   * @return If no user has logged in, then return "Cannot view reservations, not
   *         logged in\n" If the user has no reservations, then return "No
   *         reservations found\n" For all other errors, return "Failed to
   *         retrieve reservations\n"
   *
   *         Otherwise return the reservations in the following format:
   *
   *         Reservation [reservation ID] paid: [true or false]:\n" [flight 1
   *         under the reservation] [flight 2 under the reservation] Reservation
   *         [reservation ID] paid: [true or false]:\n" [flight 1 under the
   *         reservation] [flight 2 under the reservation] ...
   *
   *         Each flight should be printed using the same format as in the
   *         {@code Flight} class.
   *
   * @see Flight#toString()
   */
  public String transaction_reservations() {
    // TODO: YOUR CODE HERE
    if (!isLogged) {
        return "Cannot view reservations, not logged in\n";
    } else {
        try {
            List<String> reservations = new ArrayList<String>();
            getReservationsStatement.setString(1, loggedUser);
            ResultSet resResults = getReservationsStatement.executeQuery();
            while (resResults.next()) {
                int rid = resResults.getInt("rid");
                int fid1 = resResults.getInt("fid1");
                int fid2 = resResults.getInt("fid2");
                int paid = resResults.getInt("paid");
                getFlightStatement.setInt(1, fid1);
                ResultSet flightResult = getFlightStatement.executeQuery();
                flightResult.next();
                int result_dayOfMonth = flightResult.getInt("day_of_month");
                String result_carrierId = flightResult.getString("carrier_id");
                String result_flightNum = flightResult.getString("flight_num");
                String result_originCity = flightResult.getString("origin_city");
                String result_destCity = flightResult.getString("dest_city");
                int result_time = flightResult.getInt("actual_time");
                int result_capacity = flightResult.getInt("capacity");
                int result_price = flightResult.getInt("price");
                Flight f = new Flight(fid1, result_dayOfMonth, result_carrierId, result_flightNum, result_originCity,
                                      result_destCity, result_time, result_capacity, result_price);
                String reservation = "Reservation " + rid + " paid: " + (paid == 1) + ":\n";
                reservation += f.toString();
                if (fid2 != 0) { // If null, converted to 0, checks if there is a second flight
                    flightResult.close();
                    getFlightStatement.setInt(1, fid2);
                    flightResult = getFlightStatement.executeQuery();
                    flightResult.next();
                    result_dayOfMonth = flightResult.getInt("day_of_month");
                    result_carrierId = flightResult.getString("carrier_id");
                    result_flightNum = flightResult.getString("flight_num");
                    result_originCity = flightResult.getString("origin_city");
                    result_destCity = flightResult.getString("dest_city");
                    result_time = flightResult.getInt("actual_time");
                    result_capacity = flightResult.getInt("capacity");
                    result_price = flightResult.getInt("price");
                    f = new Flight(fid2,result_dayOfMonth, result_carrierId, result_flightNum, result_originCity,
                                      result_destCity, result_time, result_capacity, result_price);
                    reservation += f.toString();
                }
                reservations.add(reservation);
                flightResult.close();
            }
            if (reservations.isEmpty()) { // No reservations
                return "No reservations found\n";
            }
            StringBuffer reservationString = new StringBuffer();
            for (int r = 0; r < reservations.size(); r++) {
                reservationString.append(reservations.get(r));
            }
            return reservationString.toString();
        } catch (SQLException e) {
            return "Failed to retrieve reservations\n";
        }
    }
  }

  /**
   * Implements the cancel operation.
   *
   * @param reservationId the reservation ID to cancel
   *
   * @return If no user has logged in, then return "Cannot cancel reservations,
   *         not logged in\n" For all other errors, return "Failed to cancel
   *         reservation [reservationId]\n"
   *
   *         If successful, return "Canceled reservation [reservationId]\n"
   *
   *         Even though a reservation has been canceled, its ID should not be
   *         reused by the system.
   */
  public String transaction_cancel(int reservationId) {
    // TODO: YOUR CODE HERE
    if (!isLogged) {
        return "Cannot cancel reservations, not logged in\n";
    } else {
        try {
            getReservationStatement.setInt(1, reservationId);
            ResultSet resResult = getReservationStatement.executeQuery();
            if (resResult.next()) {
                String username = resResult.getString("name");
                if (!username.equals(loggedUser)) { // Not the reservation user reserved
                    return "Failed to cancel reservation " + reservationId + "\n";
                } else { // Valid cancellation
                    int paid = resResult.getInt("paid");
                    if (paid == 1) { // Reinburse and cancel
                        updateBalance(reservationId, 1);
                    } else { // Unpaid, just cancel it
                        updateReservationStatement.clearParameters();
                        updateReservationStatement.setInt(1, 0);
                        updateReservationStatement.setInt(2, 1);
                        updateReservationStatement.setInt(3, reservationId);
                        updateReservationStatement.execute();
                    }
                    return "Canceled reservation " + reservationId + "\n";
                }
            }
            resResult.close();
            return "Failed to cancel reservation " + reservationId + "\n";
        } catch (SQLException e) {
            return "Failed to cancel reservation " + reservationId + "\n";
        }
    }
  }

  /**
   * Example utility function that uses prepared statements
   */
  private int checkFlightCapacity(int fid) throws SQLException {
    checkFlightCapacityStatement.clearParameters();
    checkFlightCapacityStatement.setInt(1, fid);
    ResultSet results = checkFlightCapacityStatement.executeQuery();
    results.next();
    int capacity = results.getInt("capacity");
    results.close();

    return capacity;
  }

  /**
   * Gets the cost of a flight given the flight id
   */
  private int getPrice(int fid) throws SQLException {
    if (fid == 0) { // Null fid
        return 0;   // Free/no cost
    } else {
        getFlightStatement.clearParameters();
        getFlightStatement.setInt(1, fid);
        ResultSet flightResult = getFlightStatement.executeQuery();
        flightResult.next();
        int price = flightResult.getInt("price");
        flightResult.close();
        return price;
    }
  }

  /**
   * Gets the logged user's balance
   */
  private int getBalance() throws SQLException {
    getUserStatement.clearParameters();
    getUserStatement.setString(1, loggedUser);
    ResultSet userResult = getUserStatement.executeQuery();
    userResult.next();
    int balance = userResult.getInt("balance");
    userResult.close();
    return balance;
  }


  /**
   * Returns reservation id
   */
  private int getId() throws SQLException {
    ResultSet idResults = getIdStatement.executeQuery();
    idResults.next();
    int id = idResults.getInt(1) + 1;
    idResults.close();
    return id;
  }

  /**
   * Updates/fails to update balance, where multiplier is -1 if
   * deducting balance (pay), 1 if increasing balance (cancel), returns true if updated, false otherwise
   */
  private boolean updateBalance(int rid, int multiplier) throws SQLException {
    getReservationStatement.clearParameters();
    getReservationStatement.setInt(1, rid);
    ResultSet reservationResult = getReservationStatement.executeQuery();
    reservationResult.next();
    int f1Price = getPrice(reservationResult.getInt("fid1"));
    int f2Price = getPrice(reservationResult.getInt("fid2"));
    int balance = getBalance() + multiplier * (f1Price + f2Price);
    if (balance < 0) { // Invalid balance amount
        return false;  // Cannot make update on payment
    } else { // Update balance and reservation
        updateBalanceStatement.setInt(1, balance);
        updateBalanceStatement.setString(2, loggedUser);
        updateBalanceStatement.execute();
        int hasPaid = 0;
        int hasCancelled = 0;
        if (multiplier == -1) { // To pay for reservation
            hasPaid = 1;
        } else { // To cancel reservation
            hasCancelled = 1;
        }
        updateReservationStatement.setInt(1, hasPaid);
        updateReservationStatement.setInt(2, hasCancelled);
        updateReservationStatement.setInt(3, rid);
        updateReservationStatement.execute();
        return true;
    }
  }

  /**
   * A class to store flight information.
   */
  class Flight {
    public int fid;
    public int dayOfMonth;
    public String carrierId;
    public String flightNum;
    public String originCity;
    public String destCity;
    public int time;
    public int capacity;
    public int price;

    public Flight(int fid, int dayOfMonth, String carrierId, String flightNum,
                  String originCity, String destCity, int time, int capacity, int price) {
        this.fid = fid;
        this.dayOfMonth = dayOfMonth;
        this.carrierId = carrierId;
        this.flightNum = flightNum;
        this.originCity = originCity;
        this.destCity = destCity;
        this.time = time;
        this.capacity = capacity;
        this.price = price;
    }

    @Override
    public String toString() {
      return "ID: " + fid + " Day: " + dayOfMonth + " Carrier: " + carrierId + " Number: " + flightNum + " Origin: "
          + originCity + " Dest: " + destCity + " Duration: " + time + " Capacity: " + capacity + " Price: " + price + "\n";
    }
  }
}
