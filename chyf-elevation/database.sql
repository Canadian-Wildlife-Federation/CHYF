
drop view chyf2.nexus_vw;
drop view chyf2.eflowpath_properties_vw;
drop view chyf2.eflowpath_vw;

--before elevation
alter table chyf2.eflowpath alter column geometry type geometry;
--after elevation
alter table chyf2.eflowpath alter column geometry type geometry(linestringz, 4617);


CREATE OR REPLACE VIEW chyf2.eflowpath_vw
AS SELECT a.id,
    a.nid,
    a.ef_type,
    a.ef_subtype,
    a.rank,
    a.length,
    a.rivernameid1,
    a.aoi_id,
    a.from_nexus_id,
    a.to_nexus_id,
    a.ecatchment_id,
    a.geometry,
    a.rivernameid2
   FROM chyf2.eflowpath a
     JOIN chyf2.aoi b ON a.aoi_id = b.id
  WHERE b.display_status = 1;


-- chyf2.eflowpath_properties_vw source

CREATE OR REPLACE VIEW chyf2.eflowpath_properties_vw
AS SELECT f.id,
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

CREATE OR REPLACE VIEW chyf2.nexus_vw
AS SELECT id,
    nexus_type,
    bank_ecatchment_id,
    geometry
   FROM chyf2.nexus a
  WHERE (id IN ( SELECT eflowpath_vw.from_nexus_id
           FROM chyf2.eflowpath_vw
        UNION
         SELECT eflowpath_vw.to_nexus_id
           FROM chyf2.eflowpath_vw));

           

