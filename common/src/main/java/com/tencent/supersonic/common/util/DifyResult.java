package com.tencent.supersonic.common.util;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DifyResult {
    private String answer;
    private boolean success;
    private String errorMessage;
    private Object rawResponse;
}
