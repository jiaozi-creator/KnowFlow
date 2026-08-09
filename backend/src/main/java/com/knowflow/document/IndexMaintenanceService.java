package com.knowflow.document;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class IndexMaintenanceService {

    private final IndexMaintenanceMapper mapper;
    private final IndexSignatureService signatureService;

    public IndexMaintenanceService(
            IndexMaintenanceMapper mapper,
            IndexSignatureService signatureService
    ) {
        this.mapper = mapper;
        this.signatureService = signatureService;
    }

    /**
     * 只标记旧索引，不自动调用 Embedding API。
     */
    public int refresh() {
        return mapper.markOutdated(signatureService.current());
    }

    /**
     * 每次后端启动后自动检查版本签名。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        refresh();
    }
}