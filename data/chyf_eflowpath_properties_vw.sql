-- View: chyf2.eflowpath_properties_vw

-- DROP VIEW chyf2.eflowpath_properties_vw;

CREATE OR REPLACE VIEW chyf2.eflowpath_properties_vw
 AS
 SELECT f.id,
    f.ef_type,
    f.ef_subtype,
    f.rank,
    f.length,
    f.rivernameid1,
    f.rivernameid2,
    f.nid,
    f.aoi_id,
    f.from_nexus_id,
    f.to_nexus_id,
    f.ecatchment_id,
    p.graph_id,
    p.mainstem_id,
    p.mainstem_seq,
    p.max_uplength,
    p.strahler_order,
    p.hack_order,
    p.horton_order,
    p.shreve_order,
    f.geometry
   FROM chyf2.eflowpath f
     JOIN chyf2.eflowpath_properties p ON p.id = f.id;

ALTER TABLE chyf2.eflowpath_properties_vw
    OWNER TO chyf;

GRANT ALL ON TABLE chyf2.eflowpath_properties_vw TO egouge;
GRANT ALL ON TABLE chyf2.eflowpath_properties_vw TO katherineo;