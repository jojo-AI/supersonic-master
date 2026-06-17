package com.tencent.supersonic.chat.api.pojo.response;

import com.tencent.supersonic.common.pojo.QueryAuthorization;
import com.tencent.supersonic.common.pojo.QueryColumn;
import com.tencent.supersonic.headless.api.pojo.AggregateInfo;
import com.tencent.supersonic.headless.api.pojo.SchemaElement;
import com.tencent.supersonic.headless.api.pojo.SemanticParseInfo;
import com.tencent.supersonic.headless.api.pojo.response.QueryState;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class IntentResult {
    private String intentType;
    private String intentQuery;
}
