CREATE TABLE IF NOT EXISTS capability (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(90)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_capability_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS capability_technology (
    capability_id BIGINT NOT NULL,
    technology_id BIGINT NOT NULL,
    PRIMARY KEY (capability_id, technology_id),
    CONSTRAINT fk_ct_capability FOREIGN KEY (capability_id) REFERENCES capability(id)
);
