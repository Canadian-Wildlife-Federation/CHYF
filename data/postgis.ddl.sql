CREATE SCHEMA chyf;

CREATE TABLE chyf.elementary_catchment (
	area float8 NOT NULL,
	d2w2d_mean float8 NULL,
	d2w2d_max float8 NULL,
	elv_min float8 NULL,
	elv_max float8 NULL,
	elv_mean float8 NULL,
	slope_min float8 NULL,
	slope_max float8 NULL,
	slope_mean float8 NULL,
	north_pct float8 NULL,
	south_pct float8 NULL,
	east_pct float8 NULL,
	west_pct float8 NULL,
	flat_pct float8 NULL,
	geometry geometry(POLYGON, 4326) NULL
);
CREATE INDEX elementary_catchment_geometry_idx ON chyf.elementary_catchment USING gist (geometry);


CREATE TABLE chyf.flowpath (
	"type" varchar NOT NULL,
	"rank" varchar NOT NULL,
	length float8 NOT NULL,
	"name" varchar NULL,
	nameid varchar NULL,
	geometry geometry(LINESTRING, 4326) NULL,
	CONSTRAINT flowpath_check CHECK (((type)::text = ANY ((ARRAY['Observed'::character varying, 'Constructed'::character varying, 'Bank'::character varying, 'Inferred'::character varying])::text[]))),
	CONSTRAINT flowpath_check_1 CHECK (((rank)::text = ANY ((ARRAY['Primary'::character varying, 'Secondary'::character varying])::text[])))
);
CREATE INDEX flowpath_geometry_idx ON chyf.flowpath USING gist (geometry);


CREATE TABLE chyf.waterbody (
	area float8 NOT NULL,
	definition int4 NOT NULL, -- Classifies the waterbody into subtype.  Valid values (4-Lake, 9-Pond, 6-River, 1-Canal)
	geometry geometry(POLYGON, 4326) NULL,
	CONSTRAINT waterbody_check CHECK ((definition = ANY (ARRAY[4, 9, 6, 1])))
);
CREATE INDEX waterbody_geometry_idx ON chyf.waterbody USING gist (geometry);
COMMENT ON COLUMN chyf.waterbody.definition IS 'Classifies the waterbody into subtype.  Valid values (4-Lake, 9-Pond, 6-River, 1-Canal)';


CREATE TABLE chyf.working_limit (
	geometry geometry(POLYGON, 4326) NULL
);
CREATE INDEX working_limit_geometry_idx ON chyf.working_limit USING gist (geometry);
