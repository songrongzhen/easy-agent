import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

/**
 * 从 README 中的操作步骤生成示意动图，不模拟真实终端输出。 在仓库根目录使用 JDK 17 执行：java -Djava.awt.headless=true docs/scripts/GenerateUsageGifs.java
 */
public class GenerateUsageGifs {
    private static final int WIDTH = 1100;
    private static final int HEIGHT = 620;
    private static final Color BACKGROUND = new Color(15, 23, 42);
    private static final Color PANEL = new Color(30, 41, 59);
    private static final Color TEXT = new Color(226, 232, 240);
    private static final Color MUTED = new Color(148, 163, 184);
    private static final Color GREEN = new Color(52, 211, 153);
    private static final Color BLUE = new Color(96, 165, 250);
    private static final Color RED = new Color(251, 113, 133);
    private static final String FONT = "SansSerif";

    /**
     * 在仓库根目录生成 README 引用的两张示意动图。
     *
     * @param args 当前不使用命令行参数
     * @throws IOException 图片文件读写失败时抛出
     */
    public static void main(String[] args) throws IOException {
        Path output = Path.of("docs/images");
        Files.createDirectories(output);
        String[][] steps = {
            { "引入 Starter", "宿主：Spring Boot 3.5+ Web 应用 / JDK 17+", "easy-agent-spring-boot-starter : 0.2.0",
                "若仓库还未发布，先在组件源码目录 mvn clean install" },
            { "启用 MCP", "application.yml", "mcp.enabled: true", "llm / rag / skill.enabled: false",
                "先验证本地工具，无需模型 API Key" },
            { "定义并启动", "组件扫描范围内新增 MyTools", "@EasyTool(name = \"hello\", ...)",
                "hello(@ToolParam(name = \"name\", ...) String name)", "启动宿主应用，监听 localhost:8080" },
            { "完成初始化", "POST http://localhost:8080/mcp", "initialize  →  HTTP 200",
                "notifications/initialized  →  HTTP 202", "请求头和完整 JSON 请复制 README 的命令" },
            { "发现与调用", "tools/list  →  result.tools 包含 hello", "tools/call  →  name: hello",
                "arguments: {\"name\":\"张三\"}", "预期：isError=false / 你好，张三！" },
            { "连接客户端", "终端执行：", "claude mcp add --transport http easy-agent", "  http://localhost:8080/mcp",
                "进入 claude，用 /mcp 检查连接，再请求调用 hello" }
        };
        BufferedImage[] quickstart = new BufferedImage[steps.length];
        for (int i = 0; i < steps.length; i++) {
            BufferedImage frame = canvas();
            Graphics2D graphics = graphics(frame);
            title(graphics, "Easy Agent · 从配置到调用工具", "MCP QUICKSTART / 操作步骤示意");
            for (int j = 0; j < steps.length; j++) {
                int x = 44 + j * 170;
                graphics.setColor(j == i ? BLUE : PANEL);
                graphics.fillRoundRect(x, 124, 158, 50, 14, 14);
                text(graphics, (j + 1) + ". " + steps[j][0], x + 12, 156, 18, j == i ? BACKGROUND : MUTED);
            }
            graphics.setColor(PANEL);
            graphics.fillRoundRect(44, 200, 1010, 320, 22, 22);
            text(graphics, steps[i][0], 76, 247, 30, BLUE);
            for (int line = 1; line < steps[i].length; line++) {
                text(graphics, steps[i][line], 76, 280 + line * 43, 23, TEXT);
            }
            text(graphics, "步骤示意，非终端录屏。可复制命令、完整配置和预期响应见 README。", 44, 575, 19, MUTED);
            graphics.dispose();
            quickstart[i] = frame;
        }
        writeGif(output.resolve("mcp-quickstart.gif"), quickstart);
        int[] counts = { 0, 1, 9, 10, 11, 0 };
        BufferedImage[] limits = new BufferedImage[counts.length];
        for (int i = 0; i < counts.length; i++) {
            BufferedImage frame = canvas();
            Graphics2D graphics = graphics(frame);
            title(graphics, "工具限流 · 10 次额度如何使用", "单实例 / 每个工具 / 1 秒固定窗口 / 规则示意");
            int used = Math.min(counts[i], 10);
            boolean rejected = counts[i] > 10;
            text(graphics, "1. 注册 ToolRateLimiter Bean   2. 开启 rate-limit.enabled", 44, 153, 24, BLUE);
            text(graphics, "同一工具的 MCP、Agent、Java 调用共享额度", 44, 204, 23, TEXT);
            for (int slot = 0; slot < 10; slot++) {
                graphics.setColor(slot < used ? GREEN : PANEL);
                graphics.fillRoundRect(44 + slot * 102, 248, 88, 88, 12, 12);
                text(graphics, String.valueOf(slot + 1), 70 + slot * 102, 302, 25, slot < used ? BACKGROUND : MUTED);
            }
            String status = i == 5 ? "窗口结束 → 重置额度 → 可以再次获得许可"
                : rejected ? "第 11 次申请 → 拒绝，不进入业务方法"
                    : used == 0 ? "窗口开始：已用 0 / 10" : "第 " + used + " 次申请 → 允许，已用 " + used + " / 10";
            text(graphics, status, 44, 395, 30, rejected ? RED : GREEN);
            text(graphics, rejected ? "TOOL_RATE_LIMITED / RATE_LIMIT / retryAfterMillis"
                : "授权检查 → 参数绑定 → 申请许可 → 执行业务", 44, 451, 24, TEXT);
            text(graphics, "拒绝不自动重试；业务重试需重新申请许可。", 44, 505, 22, MUTED);
            text(graphics, "固定窗口边界可集中放行；多实例额度不共享。完整实现见 README。", 44, 575, 19, MUTED);
            graphics.dispose();
            limits[i] = frame;
        }
        writeGif(output.resolve("tool-rate-limit.gif"), limits);
        ImageIO.write(quickstart[4], "png",
            Path.of(System.getProperty("java.io.tmpdir"), "easy-agent-quickstart-preview.png").toFile());
        ImageIO.write(limits[4], "png",
            Path.of(System.getProperty("java.io.tmpdir"), "easy-agent-limit-preview.png").toFile());
        System.out.println("Generated docs/images/mcp-quickstart.gif and tool-rate-limit.gif");
    }

    private static BufferedImage canvas() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = graphics(image);
        graphics.setColor(BACKGROUND);
        graphics.fillRect(0, 0, WIDTH, HEIGHT);
        graphics.dispose();
        return image;
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return graphics;
    }

    private static void title(Graphics2D graphics, String title, String subtitle) {
        text(graphics, title, 44, 58, 34, TEXT);
        text(graphics, subtitle, 44, 94, 19, MUTED);
    }

    private static void text(Graphics2D graphics, String value, int x, int y, int size, Color color) {
        graphics.setFont(new Font(FONT, Font.PLAIN, size));
        graphics.setColor(color);
        if (x + graphics.getFontMetrics().stringWidth(value) > WIDTH - 30) {
            throw new IllegalArgumentException("Text exceeds frame width: " + value);
        }
        graphics.drawString(value, x, y);
    }

    private static void writeGif(Path path, BufferedImage[] frames) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("gif");
        ImageWriter writer = writers.next();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(path.toFile())) {
            writer.setOutput(stream);
            writer.prepareWriteSequence(null);
            for (int i = 0; i < frames.length; i++) {
                IIOMetadata metadata = writer.getDefaultImageMetadata(
                    ImageTypeSpecifier.createFromRenderedImage(frames[i]), writer.getDefaultWriteParam());
                String format = metadata.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
                IIOMetadataNode control = (IIOMetadataNode) root.getElementsByTagName("GraphicControlExtension")
                    .item(0);
                control.setAttribute("disposalMethod", "none");
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", "FALSE");
                control.setAttribute("delayTime", "230");
                control.setAttribute("transparentColorIndex", "0");
                if (i == 0) {
                    IIOMetadataNode extensions = new IIOMetadataNode("ApplicationExtensions");
                    IIOMetadataNode loop = new IIOMetadataNode("ApplicationExtension");
                    loop.setAttribute("applicationID", "NETSCAPE");
                    loop.setAttribute("authenticationCode", "2.0");
                    loop.setUserObject(new byte[] { 1, 0, 0 });
                    extensions.appendChild(loop);
                    root.appendChild(extensions);
                }
                metadata.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(frames[i], null, metadata), writer.getDefaultWriteParam());
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
    }
}
