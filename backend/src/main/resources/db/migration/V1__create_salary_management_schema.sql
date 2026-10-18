CREATE TABLE departments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT ck_departments_name_not_blank CHECK (name ~ '[^[:space:]]')
);

CREATE UNIQUE INDEX uq_departments_name_case_insensitive ON departments (lower(name));

CREATE TABLE countries (
    code VARCHAR(2) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT ck_countries_code CHECK (code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_countries_name_not_blank CHECK (name ~ '[^[:space:]]')
);

CREATE TABLE currencies (
    code VARCHAR(3) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    minor_units SMALLINT NOT NULL,
    CONSTRAINT ck_currencies_code CHECK (code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_currencies_name_not_blank CHECK (name ~ '[^[:space:]]'),
    CONSTRAINT ck_currencies_minor_units CHECK (minor_units BETWEEN 0 AND 4)
);

CREATE TABLE employees (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_number VARCHAR(32) NOT NULL,
    full_name VARCHAR(200) NOT NULL,
    department_id BIGINT NOT NULL REFERENCES departments (id) ON DELETE RESTRICT,
    country_code VARCHAR(2) NOT NULL REFERENCES countries (code) ON DELETE RESTRICT,
    salary_amount NUMERIC(19,4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL REFERENCES currencies (code) ON DELETE RESTRICT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_employees_employee_number UNIQUE (employee_number),
    CONSTRAINT ck_employees_employee_number CHECK (employee_number ~ '^[A-Z0-9][A-Z0-9_-]{0,31}$'),
    CONSTRAINT ck_employees_full_name_not_blank CHECK (full_name ~ '[^[:space:]]'),
    CONSTRAINT ck_employees_salary_positive_finite CHECK (salary_amount > 0 AND salary_amount <> 'NaN'::NUMERIC),
    CONSTRAINT ck_employees_version_nonnegative CHECK (version >= 0)
);

CREATE INDEX idx_employees_department_id ON employees (department_id, id);
CREATE INDEX idx_employees_country_id ON employees (country_code, id);
