/*
  Copyright (c) 2019 Criteo

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/

package com.criteo.vips;

import org.junit.*;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.awt.*;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static com.criteo.vips.VipsImageImpl.JPGQuality;
import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.*;

@RunWith(Theories.class)
public class VipsImageImplTest {
    private static double Delta = 0.0001;
    private static double TrimThreshold = 30.0;
    private static PixelPacket WhitePixel = new PixelPacket(255.0, 255.0, 255.0);
    private static PixelPacket TransparentPixel = new PixelPacket(255.0, 255.0, 255.0, 0.0);

    private static Map<String, byte[]> SignatureByExtension = new HashMap<>();
    static {
        SignatureByExtension.put(".jpg", new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF });
        SignatureByExtension.put(".png", new byte[] { (byte) 137, 80, 78, 71, 13, 10, 26, 10 });
        SignatureByExtension.put(".webp", new byte[] { 'R', 'I', 'F', 'F' });
        SignatureByExtension.put(".gif", new byte[] { 'G', 'I', 'F' });
    }

    @DataPoints("filenames")
    public static String[] Filenames = {
            "in_vips.jpg",
            "transparent.png",
            "logo.webp",
            "cat.gif",
            "02.gif"
    };

    @BeforeClass

    public static void onceExecutedBeforeAll() {
        VipsContext.setLeak(true);
        VipsContext.setMaxCache(0);
        VipsContext.setMaxCacheMem(0);
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Theory
    public void TestShouldOpenCorrectly(@FromDataPoints("filenames") String filename) throws IOException, VipsException {
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer(filename);
        byte[] bufferArray = VipsTestUtils.getByteArray(filename);
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());
        img.release();
        img = new VipsImageImpl(bufferArray, bufferArray.length);
        img.release();
    }

    @Theory
    public void TestShouldOpenCorrectlyFromVipsImage(@FromDataPoints("filenames") String filename) throws IOException, VipsException {
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer(filename);
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());
        VipsImageImpl copy = new VipsImageImpl(img, WhitePixel);

        assertEquals(WhitePixel, copy.getPoint(new Point(0, 0)));
        assertEquals(img.getWidth(), copy.getWidth());
        assertEquals(img.getHeight(), copy.getHeight());
        img.release();
        copy.release();
    }

    @Theory
    public void TestWriteFromDirectByteBufferShouldNotThrows(@FromDataPoints("filenames") String filename,
                                                             ImageFormat output,
                                                             boolean strip)
            throws IOException, VipsException {
        Assume.assumeTrue(output != ImageFormat.GIF);
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer(filename);
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());

        try {
            byte[] out = img.writeToArray(output, JPGQuality, strip);
        } catch (VipsException e) {
            fail();
        }
        img.release();
    }

    @Theory
    public void TestWriteFromByteArrayShouldNotThrows(@FromDataPoints("filenames") String filename,
                                                      ImageFormat output,
                                                      boolean strip)
            throws IOException, VipsException {
        Assume.assumeTrue(output != ImageFormat.GIF);
        byte[] buffer = VipsTestUtils.getByteArray(filename);
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.length);

        try {
            byte[] out = img.writeToArray(output, JPGQuality, strip);
        } catch (VipsException e) {
            junit.framework.Assert.fail();
        }
        img.release();
    }

    @Test
    public void TestReturnCorrectDimensions() throws IOException, VipsException {
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer("in_vips.jpg");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());
        int expectedWidth = 1920;
        int expectedHeight = 1080;

        assertEquals(expectedWidth, img.getWidth());
        assertEquals(expectedHeight, img.getHeight());
        img.release();
    }

    @Test
    public void TestShouldReturnCorrectFirstPixelValue() throws IOException, VipsException {
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer("in_vips.jpg");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());

        PixelPacket pixel = img.getPoint(new Point(0, 0));
        PixelPacket expected = new PixelPacket(0.0, 81.0, 216.0, 255.0);
        assertTrue(expected.equals(pixel));
        img.release();
    }

    @Test
    public void TestShouldHandleTransparentPixel() throws IOException, VipsException {
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer("transparent.png");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());

        PixelPacket pixel = img.getPoint(new Point(0, 0));
        PixelPacket expected = new PixelPacket(0.0, 0.0, 0.0, 0.0);
        assertTrue(expected.equals(pixel));
        img.release();
    }

    @Test
    public void TestShouldResizeAndKeepAspectRatio() throws IOException, VipsException {
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer("in_vips.jpg");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());

        double aspectRatio = (double) img.getWidth() / (double) img.getHeight();
        img.resize(new Dimension(800,800), false);
        assertEquals(800, img.getWidth());
        assertEquals(450, img.getHeight());
        assertEquals(aspectRatio, (double) img.getWidth() / (double) img.getHeight(), Delta);
        img.release();
    }

    @Test
    public void TestShouldResizeWithExactDimension() throws IOException, VipsException {
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer("in_vips.jpg");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());

        img.resize(new Dimension(800,800), true);
        assertEquals(800, img.getWidth());
        assertEquals(800, img.getHeight());
        img.release();
    }

    @Test
    public void TestShouldFlattenTransparentBackground() throws IOException, VipsException {
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer("transparent.png");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());
        PixelPacket blue = new PixelPacket(0, 0, 255);

        img.flatten(blue);
        PixelPacket topLeft = img.getPoint(new Point(0, 0));
        assertEquals(topLeft.r, blue.r);
        assertEquals(topLeft.g, blue.g);
        assertEquals(topLeft.b, blue.b);
        img.release();
    }

    @Test
    public void TestShouldCropCorrectly() throws IOException, VipsException {
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer("in_vips.jpg");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());

        img.crop(new Rectangle(0, 0, 50, 50));
        assertEquals(50, img.getWidth());
        assertEquals(50, img.getHeight());
        img.release();
    }

    @Test
    public void TestShouldNotCropIfOutOfBoundCropBox() throws IOException, VipsException {
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer("in_vips.jpg");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());
        int w = img.getWidth();
        int h = img.getHeight();
        Rectangle box = new Rectangle(0, 0, w + 1, h + 1);

        try {
            img.crop(box);
            fail("Should throw VipsException");
        } catch (VipsException e) {
        } finally {
            img.release();
        }
    }

    @Test
    public void TestShouldNotCropIfZeroWidthAndHeightBox() throws IOException, VipsException {
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer("white.png");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());
        int w = img.getWidth();
        int h = img.getHeight();
        Rectangle box = new Rectangle(w, h, 0, 0);

        try {
            img.crop(box);
            fail("Should throw VipsException");
        } catch (VipsException e) {
        } finally {
            img.release();
        }
    }

    @Test
    public void TestShouldPadCorrectly() throws IOException, VipsException {
        PixelPacket pixel = new PixelPacket(255.0, 255.0, 255.0, 255.0);
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer("in_vips.jpg");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());

        int w = img.getWidth();
        int h = img.getHeight();
        // Surround image with a 10-pixel white band
        img.pad(new Dimension(w + 20, h + 20), pixel, Gravity.CENTRE);
        assertEquals(w + 20, img.getWidth());
        assertEquals(h + 20, img.getHeight());
        assertTrue(pixel.equals(img.getPoint(new Point(0, 0))));
        assertTrue(pixel.equals(img.getPoint(new Point(img.getWidth() - 1, 0))));
        assertTrue(pixel.equals(img.getPoint(new Point(0, img.getHeight() - 1))));
        assertTrue(pixel.equals(img.getPoint(new Point(img.getWidth() - 1, img.getHeight() - 1))));
        img.release();
    }

    @Test
    public void TestShouldPadPNGWithTransparentPixel() throws IOException, VipsException {
        PixelPacket pixel = new PixelPacket(0.0, 0.0, 255.0, 50.0);
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer("transparent.png");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());

        // Surround image with a 10-pixel blue band
        img.pad(new Dimension(img.getWidth() + 20, img.getHeight() + 20), pixel, Gravity.CENTRE);
        assertTrue(pixel.equals(img.getPoint(new Point(0, 0))));
        assertTrue(pixel.equals(img.getPoint(new Point(img.getWidth() - 1, 0))));
        assertTrue(pixel.equals(img.getPoint(new Point(0, img.getHeight() - 1))));
        assertTrue(pixel.equals(img.getPoint(new Point(img.getWidth() - 1, img.getHeight() - 1))));
        img.release();
    }

    @Test
    public void TestShouldPadJPGAndIgnoreAlphaChannel() throws IOException, VipsException {
        PixelPacket expected = new PixelPacket(0.0, 0.0, 255.0, 255.0);
        PixelPacket pixel = new PixelPacket(0.0, 0.0, 255.0, 50.0);
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer("in_vips.jpg");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());

        // Surround image with a 10-pixel blue band
        img.pad(new Dimension(img.getWidth() + 20, img.getHeight() + 20), pixel, Gravity.CENTRE);
        assertTrue(expected.equals(img.getPoint(new Point(0, 0))));
        assertTrue(expected.equals(img.getPoint(new Point(img.getWidth() - 1, 0))));
        assertTrue(expected.equals(img.getPoint(new Point(0, img.getHeight() - 1))));
        assertTrue(expected.equals(img.getPoint(new Point(img.getWidth() - 1, img.getHeight() - 1))));
        img.release();
    }

    @Test
    public void TestShouldReturnNullArrayIfGetPointFailed() throws IOException, VipsException {
        thrown.expect(VipsException.class);
        thrown.expectMessage("Unable to get image point");
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer("transparent.png");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());

        int w = img.getWidth();
        int h = img.getHeight();
        // Try to access out of bound
        try {
            img.getPoint(new Point(w + 1, h + 1));
        } finally {
            img.release();
        }
    }

    @Test
    public void TestShouldGetPointMonochrome() throws IOException, VipsException {
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer("monochrome.jpg");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());

        PixelPacket pixel = img.getPoint(new Point(0, 0));
        img.release();
        assertEquals(255.0, pixel.getRed());
        assertEquals(255.0, pixel.getGreen());
        assertEquals(255.0, pixel.getBlue());
        assertEquals(255.0, pixel.getAlpha());
    }

    @Test
    public void TestShouldGetPointMonochromeWithTransparency() throws IOException, VipsException {
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer("monochrome_with_transparency.png");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());

        int w = img.getWidth();
        int h = img.getHeight();
        PixelPacket transparentPixel = img.getPoint(new Point(0, 0));
        // Image center is not transparent
        PixelPacket pixel = img.getPoint(new Point(w / 2, h / 2));
        img.release();
        assertEquals(255.0, transparentPixel.getRed());
        assertEquals(255.0, transparentPixel.getGreen());
        assertEquals(255.0, transparentPixel.getBlue());
        assertEquals(0.0, transparentPixel.getAlpha());
        assertEquals(82.0, pixel.getRed());
        assertEquals(82.0, pixel.getGreen());
        assertEquals(82.0, pixel.getBlue());
        assertEquals(255.0, pixel.getAlpha());
    }

    @Test
    public void TestTransparentPNGShouldHasTransparency() throws IOException, VipsException {
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer("transparent.png");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());

        assertTrue(img.hasAlpha());
        img.release();
    }

    @Test
    public void TestJpgShouldNotHasTransparency() throws IOException, VipsException {
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer("in_vips.jpg");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());

        assertFalse(img.hasAlpha());
        img.release();
    }

    @Theory
    public void TestShouldWriteToArrayHasCorrectHeaderSignature(String filename, ImageFormat imageFormat) throws IOException, VipsException {
        // libvips can't save into gif format
        Assume.assumeTrue(imageFormat != ImageFormat.GIF);
        byte[] expected = SignatureByExtension.get(imageFormat.getFileExtension());
        byte[] signature = new byte[expected.length];
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer(filename);
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());

        byte[] out = img.writeToArray(imageFormat, JPGQuality, true);
        img.release();
        System.arraycopy(out, 0, signature, 0, signature.length);
        assertArrayEquals(expected, signature);
    }

    @Test
    public void TestShouldFindTrim() throws IOException, VipsException {
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer("logo_with_padding_50x50.jpg");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());

        Rectangle boundingBox = img.findTrim(TrimThreshold, WhitePixel);
        assertEquals(50, boundingBox.x);
        assertEquals(50, boundingBox.y);
        assertEquals(640, boundingBox.width);
        assertEquals(640, boundingBox.height);
        img.release();
    }

    @Test
    public void TestShouldFindTrimWithTransparent() throws IOException, VipsException {
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer("logo_with_transparent_padding_50x50.png");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());

        Rectangle boundingBox = img.findTrim(TrimThreshold, TransparentPixel);
        assertEquals(50, boundingBox.x);
        assertEquals(50, boundingBox.y);
        assertEquals(640, boundingBox.width);
        assertEquals(640, boundingBox.height);
        img.release();
    }

    @Theory
    public void TestSimplePipelineShouldNotThrow(String filename, ImageFormat imageFormat) throws IOException, VipsException {
        // libvips can't save into gif format
        Assume.assumeTrue(imageFormat != ImageFormat.GIF);
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer(filename);
        PixelPacket pixel = new PixelPacket(5.0, 255.0, 25.0);
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());
        int width;
        int height;

        try {
            img.resize(new Dimension(400, 400), false);
            width = img.getWidth();
            height = img.getHeight();
            img.crop(new Rectangle(10, 10, width - 10, height - 10));
            width = img.getWidth();
            height = img.getHeight();
            img.pad(new Dimension(width + 10, height + 10), pixel, Gravity.CENTRE);
            byte [] out = img.writeToArray(imageFormat, JPGQuality, true);
            img.release();
        } catch (VipsException e) {
            fail();
        }
    }

    @Theory
    public void TestShouldThrowAnExceptionOnCorruptedPng(ImageFormat imageFormat) throws IOException, VipsException {
        // libvips can't save into gif format
        Assume.assumeTrue(imageFormat != ImageFormat.GIF);
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer("olin.png");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());

        try {
            byte [] out = img.writeToArray(imageFormat, JPGQuality, true);
            fail();
        } catch (VipsException e) {
            // Should throw a vips exception
        } finally {
            img.release();
        }
    }

    @Test
    public void TestShouldInsertSubImage() throws IOException, VipsException {
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer("logo_with_transparent_padding_50x50.png");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());
        VipsImageImpl background = new VipsImageImpl(img, WhitePixel);

        background.compose(img);
        assertTrue(WhitePixel.equals(background.getPoint(new Point(0,0))));
        img.release();
        background.release();
    }

    @Test
    public void TestGetPointShouldReturn256bitsValue() throws IOException, VipsException {
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer("white_48_bits.png");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());

        PixelPacket p = img.getPoint(new Point(0, 0));
        assertEquals(255.0, p.getRed());
        assertEquals(255.0, p.getGreen());
        assertEquals(255.0, p.getBlue());
        img.release();
    }

    @Test
    public void TestShouldGuessColourspace() throws IOException, VipsException {
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer("in_vips.jpg");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());

        Assert.assertEquals(VipsInterpretation.sRGB, img.getInterpretation());
        img.release();
        buffer = VipsTestUtils.getDirectByteBuffer("in_vips_cmyk.jpg");
        img = new VipsImageImpl(buffer, buffer.capacity());
        Assert.assertEquals(VipsInterpretation.CMYK, img.getInterpretation());
        img.release();
    }

    @Test
    public void TestShouldReturnExactFrameNumberFromAnimatedImage() throws IOException, VipsException {
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer("cat.gif");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());

        assertEquals(5, img.getNbFrame());
        img.release();
    }

    @Test
    public void TestShouldReturnExactFrameNumberFromNonAnimatedImage() throws IOException, VipsException {
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer("in_vips.jpg");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());

        assertEquals(1, img.getNbFrame());
        img.release();
    }

    @Test
    public void TestImageWithNoMetadataReturOneFrameNumber() throws IOException, VipsException {
        ByteBuffer buffer = VipsTestUtils.getDirectByteBuffer("02.gif");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.capacity());

        assertEquals(1, img.getNbFrame());
        img.release();
    }

    @Test
    public void TestDoubleReleaseShouldNotThrow() throws IOException, VipsException {
        byte[] buffer = VipsTestUtils.getByteArray("in_vips.jpg");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.length);
        byte[] out = img.writeToArray(ImageFormat.JPG, JPGQuality, true);

        try {
            img.release();
            img.release();
        } catch (Exception e) {
            junit.framework.Assert.fail();
        }
    }

    @Theory
    public void TestWritePNGFromByteArrayShouldNotThrows(@FromDataPoints("filenames") String filename,
                                                         boolean palette,
                                                         boolean strip)
            throws IOException, VipsException {
        byte[] buffer = VipsTestUtils.getByteArray(filename);
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.length);
        int compression = 9;
        int colors = 256;

        try {
            byte[] out = img.writePNGToArray(compression, palette, colors, strip);
        } catch (VipsException e) {
            junit.framework.Assert.fail();
        }
        img.release();
    }

    @Test
    public void TestWritePNGFromByteArrayShouldShrinkOutputSize()
            throws IOException, VipsException {
        byte[] buffer = VipsTestUtils.getByteArray("logo_with_transparent_padding_50x50.png");
        VipsImageImpl img = new VipsImageImpl(buffer, buffer.length);
        int compression = 9;
        int colors = 256;

        byte[] out = img.writePNGToArray(compression, true, colors, true);
        Assert.assertTrue(buffer.length > out.length);
        img.release();
    }
}