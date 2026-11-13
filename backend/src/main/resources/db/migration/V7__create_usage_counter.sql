CREATE TABLE usage_counter (
    account  VARCHAR(100) NOT NULL,
    job_type VARCHAR(40)  NOT NULL,
    day      DATE         NOT NULL,
    count    INT          NOT NULL CHECK (count >= 0),
    PRIMARY KEY (account, job_type, day)
);
