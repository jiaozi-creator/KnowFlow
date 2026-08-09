package com.knowflow.document;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IndexMaintenanceMapper {

    @Update("""
            update document d
               set status = 'NEEDS_REINDEX',
                   updated_at = now()
              from document_version dv
             where d.current_version_id = dv.id
               and d.status = 'READY'
               and dv.index_signature is distinct from #{signature}
            """)
    int markOutdated(@Param("signature") String signature);
}