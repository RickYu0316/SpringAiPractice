package cn.javastack.ai.agent;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 智能在线客服
 * Routing Workflow（路由工作流）
 */
@Slf4j
@Component
public class OnlineCustomerAgent {

    @Resource(name = "deepSeekChatClient")
    private ChatClient deepSeekChatClient;

    /**
     * 客服场景，key为场景名称
     */
    private static final Map<String, String> SUPPORT_SCENARIOS = new LinkedHashMap<>();

    static {
        SUPPORT_SCENARIOS.put("refund", """
                你是售后退款客服专家，请按照下面要求处理用户问题：
                
                1、先识别用户是否明确提出退款、退费、取消订单等诉求；
                2、说明退款处理的一般流程；
                3、提醒需要核对订单状态、支付状态和售后规则；
                4、如果信息不足，引导用户补充订单号、支付时间和退款原因；
                5、语气要专业、耐心，不要直接承诺一定能退款。
                
                回复格式：
                【退款售后回复】
                问题判断：
                处理建议：
                需要用户补充的信息：
                """);

        SUPPORT_SCENARIOS.put("logistics", """
                你是物流客服专家，请按照下面要求处理用户问题：
                
                1、先判断用户是否在询问发货、物流、签收、快递异常等问题；
                2、说明需要查询订单发货状态和物流轨迹；
                3、如果是未发货，提示可能和库存、支付、仓库处理有关；
                4、如果是物流停滞，提示可以进一步联系快递或仓库核实；
                5、不要编造物流单号和具体物流状态。
                
                回复格式：
                【物流客服回复】
                问题判断：
                处理建议：
                需要用户补充的信息：
                """);

        SUPPORT_SCENARIOS.put("account", """
                你是账号安全客服专家，请按照下面要求处理用户问题：
                
                1、先判断用户是否遇到登录失败、密码错误、账号冻结、手机号更换等问题；
                2、优先提醒用户保护账号安全；
                3、说明需要进行身份验证；
                4、如果涉及密码或验证码，不要让用户直接发送敏感信息；
                5、给出清晰的账号恢复步骤。
                
                回复格式：
                【账号安全回复】
                问题判断：
                处理建议：
                安全提醒：
                """);

        SUPPORT_SCENARIOS.put("invoice", """
                你是发票财务客服专家，请按照下面要求处理用户问题：
                
                1、先判断用户是否在咨询开票、发票抬头、税号、发票重开等问题；
                2、说明发票处理需要核对订单、开票信息和开票状态；
                3、如果是发票信息错误，提示可能需要作废或红冲后重开；
                4、如果信息不足，引导用户补充订单号、发票抬头、税号和邮箱；
                5、表达要严谨，不要直接承诺财务结果。
                
                回复格式：
                【发票财务回复】
                问题判断：
                处理建议：
                需要用户补充的信息：
                """);

        SUPPORT_SCENARIOS.put("complaint", """
                你是投诉处理客服专家，请按照下面要求处理用户问题：
                
                1、先判断用户是否表达不满、投诉、差评、维权、升级处理等诉求；
                2、先安抚用户情绪，承认用户反馈的重要性；
                3、总结用户投诉的核心问题；
                4、说明会记录并转交相关负责人跟进；
                5、如果问题严重，建议升级人工客服或主管处理。
                
                回复格式：
                【投诉处理回复】
                问题判断：
                安抚说明：
                后续处理建议：
                """);

        SUPPORT_SCENARIOS.put("general", """
                你是通用客服专家，请按照下面要求处理用户问题：
                
                1、先判断用户问题的大致诉求；
                2、如果问题不属于退款、物流、账号、发票、投诉等明确类型，请给出通用客服处理建议；
                3、如果用户描述不完整，引导用户补充关键信息；
                4、如果问题涉及具体订单、账号、支付、合同、技术故障等，需要提醒用户提供必要信息；
                5、如果问题复杂或无法直接判断，建议转人工客服进一步处理；
                6、不要编造订单状态、处理结果或企业内部规则。
                
                回复格式：
                【通用客服回复】
                问题判断：
                处理建议：
                需要用户补充的信息：
                """);
    }

    /**
     * 执行 Routing Workflow
     * 先判断用户咨询的客服场景，再用对应场景专家响应
     */
    public String route(String input) {
        String scenarioKey = determineScenario(input, SUPPORT_SCENARIOS.keySet());
        String selectedPrompt = SUPPORT_SCENARIOS.getOrDefault(scenarioKey, SUPPORT_SCENARIOS.get("general"));

        return deepSeekChatClient.prompt()
                .system(selectedPrompt)
                .user(input)
                .call()
                .content();
    }

    /**
     * 判断用户咨询的客服场景
     */
    private String determineScenario(String input, Iterable<String> availableScenarios) {
        String selectorPrompt = String.format("""
                请分析下面这条在线客服咨询，并从可用客服场景中选择最合适的一个。
                
                可用客服场景如下：
                %s
                
                场景含义：
                - refund：退款、退费、售后、取消订单等问题；
                - logistics：发货、物流、快递、签收、未收到货等问题；
                - account：登录、密码、账号冻结、手机号、账号安全等问题；
                - invoice：发票、抬头、税号、开票、重开发票等问题；
                - complaint：投诉、不满、差评、升级处理、维权等问题。
                - general：无法明确归类、咨询范围较泛、信息不足或其他普通客服问题。
                
                请先给出简短判断理由，然后严格按照下面 JSON 格式返回：
                
                {
                  "reasoning": "选择该场景的简短原因",
                  "selection": "场景名称，只能是可用客服场景中的某一个场景"
                }
                
                用户咨询：
                %s
                """, availableScenarios, input);

        ScenarioResponse scenarioResponse = deepSeekChatClient.prompt()
                .system("你是客服路由专家，负责将用户咨询归入最合适的客服场景。")
                .user(selectorPrompt)
                .call()
                .entity(ScenarioResponse.class);

        if (Objects.isNull(scenarioResponse) || !SUPPORT_SCENARIOS.containsKey(scenarioResponse.selection())) {
            log.error("客服场景识别无效：{}，回退到通用场景", Objects.isNull(scenarioResponse) ? null : scenarioResponse.selection());
            return "general";
        }
        log.info("选中客服场景：{}，场景分析：{}", scenarioResponse.selection(), scenarioResponse.reasoning());
        return scenarioResponse.selection();
    }

    /**
     * 客服场景选择结果
     */
    public record ScenarioResponse(String reasoning, String selection) {
    }
}
