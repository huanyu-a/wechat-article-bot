package ink.icoding.wechat.article.schedule;

/**
 * 阶段停滞（硬超时或被中断）专用异常，用来把「会话卡住」与「模型/工具报错」分开。
 *
 * <p>为什么需要区分：两者此前都是 {@link IllegalStateException}，调用方（如
 * {@code DelegateTools.runSubAgent}）只捕这一种，因而无法决定「值得重试」还是「应当直接失败」。
 * 停滞是**外部原因**（网关不返回、SSE 被掐断且不回调），且此时通常没有任何工具调用发生，
 * 重试一次没有副作用；而模型报错（参数非法、余额不足等）重试只会重复失败并多付费。
 *
 * <p>继承 {@link IllegalStateException} 而非另立基类，是为了让既有 catch 点（捕
 * {@code IllegalStateException} 转为引导文本）行为不变——新增的是**可识别性**，不是新的传播路径。
 */
public class StageTimeoutException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public StageTimeoutException(String message) {
        super(message);
    }

    public StageTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
