CREATE TABLE Users (
    username VARCHAR(20) PRIMARY KEY,
    password VARBINARY(20),
    salt VARBINARY(20),
    balance int);

CREATE TABLE Reservations (
    rid INT PRIMARY KEY,
    name VARCHAR(20),
    fid1 INT,
    fid2 INT,
    paid INT,       /* 0 for unpaid, 1 for paid */
    cancelled INT); /* 0 for uncancelled, 1 for cancelled */
