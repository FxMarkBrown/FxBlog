package top.fxmarkbrown.blog.utils;

import top.fxmarkbrown.blog.common.RedisConstants;
import top.fxmarkbrown.blog.dto.Captcha;
import top.fxmarkbrown.blog.exception.ServiceException;
import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Base64;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class CaptchaUtil {
    private static final Logger log = LoggerFactory.getLogger(CaptchaUtil.class);

    /**
     * 网络图片地址（302重定向返回随机壁纸）
     **/
    private final static String IMG_URL = "https://v2.xxapi.cn/api/wallpaper?return=302";

    /**
     * 内置验证码背景图数量
     **/
    private final static int FALLBACK_IMAGE_COUNT = 5;

    /**
     * 拼图验证码允许偏差
     **/
    private final static Integer ALLOW_DEVIATION = 3;

    /**
     * 入参校验设置默认值
     **/
    public static void checkCaptcha(Captcha captcha) {
        //设置画布宽度默认值
        if (captcha.getCanvasWidth() == null) {
            captcha.setCanvasWidth(320);
        }
        //设置画布高度默认值
        if (captcha.getCanvasHeight() == null) {
            captcha.setCanvasHeight(155);
        }
        //设置阻塞块宽度默认值
        if (captcha.getBlockWidth() == null) {
            captcha.setBlockWidth(65);
        }
        //设置阻塞块高度默认值
        if (captcha.getBlockHeight() == null) {
            captcha.setBlockHeight(55);
        }
        //设置阻塞块凹凸半径默认值
        if (captcha.getBlockRadius() == null) {
            captcha.setBlockRadius(9);
        }
        //设置图片来源默认值
        if (captcha.getPlace() == null) {
            captcha.setPlace(0);
        }
    }

    /**
     * 获取指定范围内的随机数
     **/
    public static int getNonceByRange(int start, int end) {
        Random random = new Random();
        return random.nextInt(end - start + 1) + start;
    }

    /**
     * 获取验证码资源图（优先网络，失败回退到内置图片）
     **/
    public static BufferedImage getBufferedImage(Integer place) {
        // 优先从网络获取
        BufferedImage image = fetchNetworkImage();
        if (image != null) {
            return image;
        }
        // 网络失败，回退到内置图片
        log.warn("滑块验证码背景图已回退到内置图片源");
        return loadFallbackImage();
    }

    /**
     * 从网络获取随机壁纸
     */
    private static BufferedImage fetchNetworkImage() {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(IMG_URL).toURL().openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            try (InputStream is = conn.getInputStream()) {
                BufferedImage image = ImageIO.read(is);
                if (image == null) {
                    log.warn("滑块验证码背景图远程加载失败: ImageIO 未识别有效图片, url={}", IMG_URL);
                }
                return image;
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            log.warn("滑块验证码背景图远程加载失败: url={}", IMG_URL, e);
            return null;
        }
    }

    /**
     * 加载内置的验证码背景图（classpath:captcha/1.jpg ~ 5.jpg）
     */
    private static BufferedImage loadFallbackImage() {
        try {
            int index = getNonceByRange(1, FALLBACK_IMAGE_COUNT);
            try (InputStream is = CaptchaUtil.class.getClassLoader()
                    .getResourceAsStream("captcha/" + index + ".jpg")) {
                if (is != null) {
                    return ImageIO.read(is);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        // 最终兜底：生成纯色渐变图
        return generateGradientImage(320, 155);
    }

    /**
     * 兜底生成随机渐变图片
     */
    private static BufferedImage generateGradientImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        Random random = new Random();
        Color c1 = new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256));
        Color c2 = new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256));
        g2d.setPaint(new GradientPaint(0, 0, c1, width, height, c2));
        g2d.fillRect(0, 0, width, height);
        g2d.dispose();
        return image;
    }

    /**
     * 调整图片大小
     **/
    public static BufferedImage imageResize(BufferedImage bufferedImage, int width, int height) {
        Image image = bufferedImage.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        BufferedImage resultImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics2D = resultImage.createGraphics();
        graphics2D.drawImage(image, 0, 0, null);
        graphics2D.dispose();
        return resultImage;
    }

    /**
     * 抠图，并生成阻塞块
     **/
    public static void cutByTemplate(BufferedImage canvasImage, BufferedImage blockImage, int blockWidth, int blockHeight, int blockRadius, int blockX, int blockY) {
        BufferedImage waterImage = new BufferedImage(blockWidth, blockHeight, BufferedImage.TYPE_4BYTE_ABGR);
        //阻塞块的轮廓图
        int[][] blockData = getBlockData(blockWidth, blockHeight, blockRadius);
        //创建阻塞块具体形状
        for (int i = 0; i < blockWidth; i++) {
            for (int j = 0; j < blockHeight; j++) {
                try {
                    //原图中对应位置变色处理
                    if (blockData[i][j] == 1) {
                        //背景设置为黑色
                        waterImage.setRGB(i, j, Color.BLACK.getRGB());
                        blockImage.setRGB(i, j, canvasImage.getRGB(blockX + i, blockY + j));
                        //轮廓设置为白色，取带像素和无像素的界点，判断该点是不是临界轮廓点
                        if (blockData[i + 1][j] == 0 || blockData[i][j + 1] == 0 || blockData[i - 1][j] == 0 || blockData[i][j - 1] == 0) {
                            blockImage.setRGB(i, j, Color.WHITE.getRGB());
                            waterImage.setRGB(i, j, Color.WHITE.getRGB());
                        }
                    }
                    //这里把背景设为透明
                    else {
                        blockImage.setRGB(i, j, Color.TRANSLUCENT);
                        waterImage.setRGB(i, j, Color.TRANSLUCENT);
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    //防止数组下标越界异常
                }
            }
        }
        //在画布上添加阻塞块水印
        addBlockWatermark(canvasImage, waterImage, blockX, blockY);
    }

    /**
     * 构建拼图轮廓轨迹
     **/
    private static int[][] getBlockData(int blockWidth, int blockHeight, int blockRadius) {
        int[][] data = new int[blockWidth][blockHeight];
        double po = Math.pow(blockRadius, 2);
        //随机生成两个圆的坐标，在4个方向上 随机找到2个方向添加凸/凹
        //凸/凹1
        int face1 = RandomUtils.nextInt(0,4);
        //凸/凹2
        int face2;
        //保证两个凸/凹不在同一位置
        do {
            face2 = RandomUtils.nextInt(0,4);
        } while (face1 == face2);
        //获取凸/凹起位置坐标
        int[] circle1 = getCircleCoords(face1, blockWidth, blockHeight, blockRadius);
        int[] circle2 = getCircleCoords(face2, blockWidth, blockHeight, blockRadius);
        //随机凸/凹类型
        int shape = getNonceByRange(0, 1);
        //圆的标准方程 (x-a)²+(y-b)²=r²,标识圆心（a,b）,半径为r的圆
        //计算需要的小图轮廓，用二维数组来表示，二维数组有两张值，0和1，其中0表示没有颜色，1有颜色
        for (int i = 0; i < blockWidth; i++) {
            for (int j = 0; j < blockHeight; j++) {
                data[i][j] = 0;
                //创建中间的方形区域
                if ((i >= blockRadius && i <= blockWidth - blockRadius && j >= blockRadius && j <= blockHeight - blockRadius)) {
                    data[i][j] = 1;
                }
                double d1 = Math.pow(i - Objects.requireNonNull(circle1)[0], 2) + Math.pow(j - circle1[1], 2);
                double d2 = Math.pow(i - Objects.requireNonNull(circle2)[0], 2) + Math.pow(j - circle2[1], 2);
                //创建两个凸/凹
                if (d1 <= po || d2 <= po) {
                    data[i][j] = shape;
                }
            }
        }
        return data;
    }
    /**
     * 根据朝向获取圆心坐标
     */
    private static int[] getCircleCoords(int face, int blockWidth, int blockHeight, int blockRadius) {
        //上
        if (0 == face) {
            return new int[]{blockWidth / 2 - 1, blockRadius};
        }
        //左
        else if (1 == face) {
            return new int[]{blockRadius, blockHeight / 2 - 1};
        }
        //下
        else if (2 == face) {
            return new int[]{blockWidth / 2 - 1, blockHeight - blockRadius - 1};
        }
        //右
        else if (3 == face) {
            return new int[]{blockWidth - blockRadius - 1, blockHeight / 2 - 1};
        }
        return null;
    }
    /**
     * 在画布上添加阻塞块水印
     */
    private static void addBlockWatermark(BufferedImage canvasImage, BufferedImage blockImage, int x, int y) {
        Graphics2D graphics2D = canvasImage.createGraphics();
        graphics2D.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.8f));
        graphics2D.drawImage(blockImage, x, y, null);
        graphics2D.dispose();
    }
    /**
     * BufferedImage转BASE64
     */
    public static String toBase64(BufferedImage bufferedImage, String type) {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, type, byteArrayOutputStream);
            String base64 = Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray());
            return String.format("data:image/%s;base64,%s", type, base64);
        } catch (IOException e) {
            System.out.println("图片资源转换BASE64失败");
            //异常处理
            return null;
        }
    }


    /**
     * 校验验证码
     **/
    public static void checkImageCode(String imageKey, String imageCode) {
        RedisUtil redisUtil = SpringUtil.getBean(RedisUtil.class);
        Object text = redisUtil.get(RedisConstants.SLIDER_CAPTCHA_CODE_KEY + imageKey);
        if(Objects.isNull(text)){
            throw new ServiceException("验证码已失效");
        }
        // 根据移动距离判断验证是否成功
        if (Math.abs(Integer.parseInt(text.toString()) - Integer.parseInt(imageCode)) > ALLOW_DEVIATION) {
            throw new ServiceException("验证失败，请控制拼图对齐缺口");
        }
    }
    /**
     * 缓存验证码，有效期1分钟
     **/
    public static void saveImageCode(String key, String code) {
        RedisUtil redisUtil = SpringUtil.getBean(RedisUtil.class);
        redisUtil.set(RedisConstants.SLIDER_CAPTCHA_CODE_KEY + key, code, RedisConstants.MINUTE_EXPIRE, TimeUnit.SECONDS);
    }

    /**
     * 获取验证码拼图（生成的抠图和带抠图阴影的大图及抠图坐标）
     **/
    public static void getCaptcha(Captcha captcha) {
        //参数校验
        checkCaptcha(captcha);
        //获取画布的宽高
        int canvasWidth = captcha.getCanvasWidth();
        int canvasHeight = captcha.getCanvasHeight();
        //获取阻塞块的宽高/半径
        int blockWidth = captcha.getBlockWidth();
        int blockHeight = captcha.getBlockHeight();
        int blockRadius = captcha.getBlockRadius();
        //获取资源图
        BufferedImage canvasImage = getBufferedImage(captcha.getPlace());
        //调整原图到指定大小
        canvasImage = imageResize(canvasImage, canvasWidth, canvasHeight);
        //随机生成阻塞块坐标
        int blockX = getNonceByRange(blockWidth, canvasWidth - blockWidth - 10);
        int blockY = getNonceByRange(10, canvasHeight - blockHeight + 1);
        //阻塞块
        BufferedImage blockImage = new BufferedImage(blockWidth, blockHeight, BufferedImage.TYPE_4BYTE_ABGR);
        //新建的图像根据轮廓图颜色赋值，源图生成遮罩
        cutByTemplate(canvasImage, blockImage, blockWidth, blockHeight, blockRadius, blockX, blockY);
        // 移动横坐标
        String nonceStr = UUID.randomUUID().toString().replaceAll("-", "");
        // 缓存
        saveImageCode(nonceStr,String.valueOf(blockX));
        //设置返回参数
        captcha.setNonceStr(nonceStr);
        captcha.setBlockY(blockY);
        captcha.setBlockSrc(toBase64(blockImage, "png"));
        captcha.setCanvasSrc(toBase64(canvasImage, "png"));
    }
}
