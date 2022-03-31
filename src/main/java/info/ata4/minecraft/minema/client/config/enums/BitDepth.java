package info.ata4.minecraft.minema.client.config.enums;

import static org.lwjgl.opengl.GL11.*;

public enum BitDepth
{
    BIT8( 1, GL_UNSIGNED_BYTE, "-f rawvideo -pix_fmt bgr24 -s %WIDTH%x%HEIGHT% -r %FPS% -i - -vf %DEFVF% -c:v libx264 -preset ultrafast -tune zerolatency -qp 6 -pix_fmt yuv420p %NAME%_depth.mp4"),
    BIT16( 2, GL_UNSIGNED_SHORT, "-f rawvideo -pix_fmt bgr48be -s %WIDTH%x%HEIGHT% -r %FPS% -i - -vf %DEFVF% -preset ultrafast -tune zerolatency -qp 6 -pix_fmt bgr48be %NAME%_depth_%d.png"),
    BIT32( 4, GL_FLOAT, "-f rawvideo -pix_fmt grayf32be -s %WIDTH%x%HEIGHT% -r %FPS% -i - -vf %DEFVF% -preset ultrafast -tune zerolatency -qp 6 -compression zip1 -pix_fmt gbrpf32be %NAME%_depth_%d.exr");

    private int depth;
    private int glFormat;
    private String ffmpegParams;

    BitDepth(int depth, int glFormat, String ffmpegParams)
    {
        this.depth = depth;
        this.glFormat = glFormat;
        this.ffmpegParams = ffmpegParams;
    }

    public int getBytesPerChannel()
    {
        return this.depth;
    }

    public String getParams()
    {
        return this.ffmpegParams;
    }

    public int getFormat()
    {
        return this.glFormat;
    }
}
