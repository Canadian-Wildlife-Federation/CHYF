-- CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

create schema raw;
create schema working;

-- aoi table with status column
create table raw.aoi(
 id uuid,
 name varchar unique,
 geometry geometry(POLYGON, 4326) NOT NULL,
 status varchar not null default 'READY' check (status in ('READY', 'FP_PROCESSING', 'FP_ERROR', 'FP_DONE', 'WS_PROCESSING','WS_DONE','WS_ERROR')),
 processing_start_datetime timestamp,
 processing_end_datetime timestamp,
 processing_parameters varchar,
 primary key (id)
);

-- feature tables
create table raw.terminal_node(
 aoi_id uuid references raw.aoi(id),
 flow_direction integer CHECK (flow_direction IN (1, 2)),
 geometry geometry(POINT, 4326) NOT NULL
);

create table raw.shoreline(
 aoi_id uuid references raw.aoi(id),
 geometry geometry(LINESTRING, 4326) NOT NULL
);

create table raw.eflowpath(
 ef_type integer NOT NULL CHECK (ef_type IN (1,2,3,4)),
 ef_subtype integer CHECK (ef_subtype BETWEEN 0 and 100),
 direction_known integer CHECK (direction_known IN (1, -1)),
 name_string varchar,
 aoi_id uuid references raw.aoi(id),
 geometry geometry(LINESTRING, 4326) NOT NULL
);

create table raw.ecatchment(
 ec_type integer NOT NULL CHECK (ec_type IN (1,2,3,4,5)),
 ec_subtype integer CHECK (ec_subtype BETWEEN 0 and 100),
 name_string varchar,
 aoi_id uuid references raw.aoi(id),
 geometry geometry(POLYGON, 4326) NOT NULL
);