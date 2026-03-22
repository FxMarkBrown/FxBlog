package top.fxmarkbrown.blog.model.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiConversationRouteDecision {

    private String route = AiConversationRoute.DIRECT_CHAT.name();

    private String reason = "";

    private Integer confidence = 0;
}
