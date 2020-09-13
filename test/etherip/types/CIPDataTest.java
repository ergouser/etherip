/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.types;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import etherip.TestSettings;
import etherip.data.Identity;
import etherip.types.CIPData.Type;
import etherip.util.Hexdump;

/**
 * JUnit test of {@link CIPData}
 *
 * @author Kay Kasemir
 */
@SuppressWarnings("nls")
public class CIPDataTest {
  /** AB device Info. */
  protected static final Identity abDeviceInfo;

  /** Omron device Info. */
  protected static final Identity omronDeviceInfo;

  static {
    Identity identity = Identity.LAST_DEVICE_INFO_FROM_PLC;

    /** Create the identities. */
    abDeviceInfo = new Identity();
    abDeviceInfo.setVendorId(Identity.VENDOR_AB);
    abDeviceInfo.setProductCode((short)0xFF);
    abDeviceInfo.setRevision(new Integer[] {0xFF});
    abDeviceInfo.setSerialNumber("Compatibility");
    abDeviceInfo.setStatus("0");
    abDeviceInfo.setProductName("ABCompatibilityDevice");

    omronDeviceInfo = new Identity();
    omronDeviceInfo.setVendorId(Identity.VENDOR_OMRON);
    omronDeviceInfo.setProductCode((short)0xFF);
    omronDeviceInfo.setRevision(new Integer[] {0xFF});
    omronDeviceInfo.setSerialNumber("Compatibility");
    omronDeviceInfo.setStatus("0");
    omronDeviceInfo.setProductName("NJ/NXCompatibilityDevice");

    Identity.LAST_DEVICE_INFO_FROM_PLC = identity; // reset this so that it continues to be the AB compatibility  device.
  }

  @Test
  public void testFloat() throws Exception {
    // Decode
    final CIPData data = new CIPData(CIPData.Type.REAL,
        new byte[] { (byte) 0xF9, (byte) 0x0F, (byte) 0x49, (byte) 0x40 });

    assertThat(CIPData.Type.REAL, equalTo(data.getType()));
    assertThat(1, equalTo(data.getElementCount()));
    assertThat(data.toString(), equalTo("CIP_REAL (0x00CA): [3.1416]"));

    assertThat(true, equalTo(data.isNumeric()));
    assertThat("3.1416", equalTo(data.getNumber(0).toString()));

    // Encode
    final ByteBuffer buf = TestSettings.getBuffer();
    data.encode(buf);
    buf.flip();
    assertThat(Hexdump.toHex(buf).trim(), equalTo("0000 - CA 00 01 00 F9 0F 49 40"));

    // Modify
    data.set(0, 42.0);
    assertThat(data.toString(), equalTo("CIP_REAL (0x00CA): [42.0]"));
  }

  @Test
  public void testString() throws Exception {
    final byte[] data = new byte[] { (byte) 0xCE, (byte) 0x0F, 5, 0, 0, 0, 'H', 'e', 'l', 'l', 'o' };
    final CIPData value = new CIPData(CIPData.Type.STRUCT, data);

    assertThat(CIPData.Type.STRUCT, equalTo(value.getType()));

    final String txt = value.getString();
    System.out.println(txt);
    assertThat(txt, equalTo("Hello"));
    assertThat(value.toString(), equalTo("CIP_STRUCT (0x02A0): STRUCT_STRING (0x0FCE) 'Hello', len 5"));
  }

  @Test
  public void testStrings() throws Exception {
    // Decode
    final CIPData omronData = new CIPData(CIPData.Type.OMRON_STRING, "OmronString");

    assertThat(CIPData.Type.OMRON_STRING, equalTo(omronData.getType()));
    assertThat(1, equalTo(omronData.getElementCount())); //
    assertThat(omronData.toString(), equalTo("CIP_OMRON_STRING (0x00D0): \"OmronString\""));

    assertThat(false, equalTo(omronData.isNumeric()));
    assertThat("OmronString", equalTo(omronData.getString()));

    final CIPData abData = new CIPData(CIPData.Type.STRUCT_STRING, "RockwellString");

    assertThat(CIPData.Type.STRUCT_STRING, equalTo(abData.getType()));
    assertThat(1, equalTo(abData.getElementCount())); //
    assertThat(abData.toString(), equalTo("CIP_STRUCT_STRING (0x0FCE): STRUCT_STRING (0x0FCE) 'RockwellString', len 14"));

    assertThat(false, equalTo(abData.isNumeric()));
    // getString is not symetric with constructor - assertThat("RockwellString", equalTo(abData.getString()));

  }
  
  @Test
  public void testCreateType() throws Exception {
    final CIPData value = new CIPData(Type.INT, 3);
    value.set(0, 1);
    value.set(1, 2);
    value.set(2, 3);
    assertThat(value.toString(), equalTo("CIP_INT (0x00C3): [1, 2, 3]"));
  }

  @Test
  public void testInt() throws Exception {
    // Decode
    final CIPData data = new CIPData(CIPData.Type.INT,
        new byte[] { (byte) 0xF9, (byte) 0xF8, (byte) 0x49, (byte) 0x40 }); // -1799, 16457

    assertThat(CIPData.Type.INT, equalTo(data.getType()));
    assertThat(2, equalTo(data.getElementCount()));
    assertThat(data.toString(), equalTo("CIP_INT (0x00C3): [-1799, 16457]"));

    assertThat(true, equalTo(data.isNumeric()));
    assertThat("-1799", equalTo(data.getNumber(0).toString()));

    // Encode
    final ByteBuffer buf = TestSettings.getBuffer();
    data.encode(buf);
    buf.flip();
    assertThat(Hexdump.toHex(buf).trim(), equalTo("0000 - C3 00 02 00 F9 F8 49 40"));

    // Modify
    data.set(0, 42);
    assertThat(data.toString(), equalTo("CIP_INT (0x00C3): [42, 16457]"));
  }

  @Test
  public void testABBool() throws Exception {
    // Decode
    final CIPData data = new CIPData(CIPData.Type.BOOL, new byte[] { (byte) 0x1 }); // true

    assertThat(CIPData.Type.BOOL, equalTo(data.getType()));
    assertThat(1, equalTo(data.getElementCount()));
    assertThat(data.toString(), equalTo("CIP_BOOL (0x00C1): [TRUE]"));

    assertThat(true, equalTo(data.isNumeric()));
    assertThat("1", equalTo(data.getNumber(0).toString()));

    // Encode
    final ByteBuffer buf = TestSettings.getBuffer();
    data.encode(buf);
    buf.flip();
    assertThat(Hexdump.toHex(buf).trim(), equalTo("0000 - C1 00 01 00 01"));

    // Modify
    data.set(0, 42);
    assertThat(data.toString(), equalTo("CIP_BOOL (0x00C1): [TRUE]"));
    // Modify
    data.set(0, 0);
    assertThat(data.toString(), equalTo("CIP_BOOL (0x00C1): [FALSE]"));
  }

  @Test
  public void testOmronBool() throws Exception {
    // Decode
    final CIPData data = new CIPData(CIPData.Type.BOOL, new byte[] { (byte) 0x1, (byte) 0x0 }, omronDeviceInfo); // true

    assertThat(CIPData.Type.BOOL, equalTo(data.getType()));
    assertThat(1, equalTo(data.getElementCount()));
    assertThat(data.toString(), equalTo("CIP_BOOL (0x00C1): [TRUE]"));

    assertThat(true, equalTo(data.isNumeric()));
    assertThat("1", equalTo(data.getNumber(0).toString()));

    // Encode
    final ByteBuffer buf = TestSettings.getBuffer();
    data.encode(buf);
    buf.flip();
    assertThat(Hexdump.toHex(buf).trim(), equalTo("0000 - C1 00 01 00 01 00"));

    // Modify
    data.set(0, 42);
    assertThat(data.toString(), equalTo("CIP_BOOL (0x00C1): [TRUE]"));
    // Modify
    data.set(0, 0);
    assertThat(data.toString(), equalTo("CIP_BOOL (0x00C1): [FALSE]"));
  }

  @Test
  public void testUint() throws Exception {
    // Decode
    final CIPData data = new CIPData(CIPData.Type.UINT,
        new byte[] { (byte) 0xF9, (byte) 0xF8, (byte) 0x49, (byte) 0x40 }, abDeviceInfo); // -1799, 16457

    assertThat(CIPData.Type.UINT, equalTo(data.getType()));
    assertThat(2, equalTo(data.getElementCount()));
    System.out.println(data.toString());
    assertThat(data.toString(), equalTo("CIP_UINT (0x00C7): [63737, 16457]"));

    assertThat(true, equalTo(data.isNumeric()));
    assertThat("63737", equalTo(data.getNumber(0).toString()));

    // Encode
    final ByteBuffer buf = TestSettings.getBuffer();
    data.encode(buf);
    buf.flip();
    System.out.println(Hexdump.toHex(buf).trim());
    assertThat(Hexdump.toHex(buf).trim(), equalTo("0000 - C7 00 02 00 F9 F8 49 40"));

    // Modify
    data.set(0, 42);
    assertThat(data.toString(), equalTo("CIP_UINT (0x00C7): [42, 16457]"));
  }

  @Test
  public void testDint() throws Exception {
    // Decode
    final CIPData data = new CIPData(CIPData.Type.DINT, new byte[] { (byte) 0xF9, (byte) 0xF8, (byte) 0x49, (byte) 0xF0 }, abDeviceInfo); // 1078589689

    assertThat(CIPData.Type.DINT, equalTo(data.getType()));
    assertThat(1, equalTo(data.getElementCount()));
    assertThat(data.toString(), equalTo("CIP_DINT (0x00C4): [-263587591]"));

    assertThat(true, equalTo(data.isNumeric()));
    assertThat("-263587591", equalTo(data.getNumber(0).toString()));

    // Encode
    final ByteBuffer buf = TestSettings.getBuffer();
    data.encode(buf);
    buf.flip();
    assertThat(Hexdump.toHex(buf).trim(), equalTo("0000 - C4 00 01 00 F9 F8 49 F0"));

    // Modify
    data.set(0, 42);
    System.out.println(data.toString());
    assertThat(data.toString(), equalTo("CIP_DINT (0x00C4): [42]"));
  }

  @Test
  public void testLint() throws Exception {
    // Decode
    final CIPData data = new CIPData(CIPData.Type.LINT, new byte[] { (byte) 0xF9, (byte) 0xF8, (byte) 0x49, (byte) 0xF0,
        (byte) 0xF9, (byte) 0xF8, (byte) 0x49, (byte) 0xF0, 32, 0, 0, 0, 0, 0, 0, 0 }, omronDeviceInfo); //

    assertThat(CIPData.Type.LINT, equalTo(data.getType()));
    assertThat(2, equalTo(data.getElementCount()));
    assertThat(data.toString(), equalTo("CIP_LINT (0x00C5): [-1132100078945044231, 32]"));

    assertThat(true, equalTo(data.isNumeric()));
    assertThat("-1132100078945044231", equalTo(data.getNumber(0).toString()));

    // Encode
    final ByteBuffer buf = TestSettings.getBuffer();
    data.encode(buf);
    buf.flip();
    assertThat(Hexdump.toHex(buf).trim(),
        equalTo("0000 - C5 00 02 00 F9 F8 49 F0 F9 F8 49 F0 20 00 00 00 \n0010 - 00 00 00 00"));

    // Modify
    data.set(0, 42);
    assertThat(data.toString(), equalTo("CIP_LINT (0x00C5): [42, 32]"));
  }

  @Test
  public void testUlint() throws Exception {
    // Decode
    final CIPData data = new CIPData(CIPData.Type.ULINT, new byte[] { (byte) 0xF9, (byte) 0xF8, (byte) 0x49,
        (byte) 0xF0, (byte) 0xF9, (byte) 0xF8, (byte) 0x49, (byte) 0xF0, 32, 0, 0, 0, 0, 0, 0, 0 }, omronDeviceInfo); //

    assertThat(CIPData.Type.ULINT, equalTo(data.getType()));
    assertThat(2, equalTo(data.getElementCount()));
    assertThat(data.toString(), equalTo("CIP_ULINT (0x00C9): [-1132100078945044231, 32]"));

    assertThat(true, equalTo(data.isNumeric()));
    assertThat("-1132100078945044231", equalTo(data.getNumber(0).toString()));

    // Encode
    final ByteBuffer buf = TestSettings.getBuffer();
    data.encode(buf);
    buf.flip();
    assertThat(Hexdump.toHex(buf).trim(),
        equalTo("0000 - C9 00 02 00 F9 F8 49 F0 F9 F8 49 F0 20 00 00 00 \n0010 - 00 00 00 00"));

    // Modify
    data.set(0, 42);
    assertThat(data.toString(), equalTo("CIP_ULINT (0x00C9): [42, 32]"));
  }

  @Test
  public void testSint() throws Exception {
    // Decode
    final CIPData data = new CIPData(CIPData.Type.SINT, new byte[] { (byte) 0xF9 }); //

    assertThat(CIPData.Type.SINT, equalTo(data.getType()));
    assertThat(1, equalTo(data.getElementCount()));
    System.out.println(data.toString());
    assertThat(data.toString(), equalTo("CIP_SINT (0x00C2): [-7]"));

    assertThat(true, equalTo(data.isNumeric()));
    assertThat("-7", equalTo(data.getNumber(0).toString()));

    // Encode
    final ByteBuffer buf = TestSettings.getBuffer();
    data.encode(buf);
    buf.flip();
    System.out.println(Hexdump.toHex(buf).trim());
    assertThat(Hexdump.toHex(buf).trim(), equalTo("0000 - C2 00 01 00 F9"));

    // Modify
    data.set(0, 42);
    assertThat(data.toString(), equalTo("CIP_SINT (0x00C2): [42]"));
  }

  @Test
  public void testUsint() throws Exception {
    // Decode
    final CIPData data = new CIPData(CIPData.Type.USINT, new byte[] { (byte) 0xF9 }, omronDeviceInfo); //

    assertThat(CIPData.Type.USINT, equalTo(data.getType()));
    assertThat(1, equalTo(data.getElementCount()));
    System.out.println(data.toString());
    assertThat(data.toString(), equalTo("CIP_USINT (0x00C6): [249]"));

    assertThat(true, equalTo(data.isNumeric()));
    assertThat("249", equalTo(data.getNumber(0).toString()));

    // Encode
    final ByteBuffer buf = TestSettings.getBuffer();
    data.encode(buf);
    buf.flip();
    System.out.println(Hexdump.toHex(buf).trim());
    assertThat(Hexdump.toHex(buf).trim(), equalTo("0000 - C6 00 01 00 F9"));

    // Modify
    data.set(0, 42);
    assertThat(data.toString(), equalTo("CIP_USINT (0x00C6): [42]"));
  }

  @Test
  public void testOmronString() throws Exception {
    // Decode
    final CIPData data = new CIPData(CIPData.Type.OMRON_STRING, "OmronString");

    assertThat(CIPData.Type.OMRON_STRING, equalTo(data.getType()));
    assertThat(1, equalTo(data.getElementCount())); //
    assertThat(data.toString(), equalTo("CIP_OMRON_STRING (0x00D0): \"OmronString\""));

    assertThat(false, equalTo(data.isNumeric()));
    assertThat("OmronString", equalTo(data.getOmronString()));

    // Encode
    ByteBuffer buf = TestSettings.getBuffer();
    data.encode(buf);
    buf.flip();
    assertThat(Hexdump.toHex(buf).trim(),
        equalTo("0000 - D0 00 01 00 0B 00 4F 6D 72 6F 6E 53 74 72 69 6E \n0010 - 67"));

    // string from here - https://docs.oracle.com/javase/tutorial/i18n/text/stream.html
    String jaString = new String("\u65e5\u672c\u8a9e\u6587\u5b57\u5217");
    System.out.println ("Japanese String: " + jaString );
    // UTF-8
    final CIPData data0 = new CIPData(CIPData.Type.OMRON_STRING, jaString);
    buf = TestSettings.getBuffer();
    data0.encode(buf);
    buf.flip();
    assertThat(Hexdump.toHex(buf).trim(),
        equalTo("0000 - D0 00 01 00 12 00 E6 97 A5 E6 9C AC E8 AA 9E E6 \n0010 - 96 87 E5 AD 97 E5 88 97"));
    assertThat(data0.toString(), equalTo("CIP_OMRON_STRING (0x00D0): \"" + jaString + "\""));
  }

  @Test
  public void testOmronStringArray() throws Exception {
    // Expect failure.
    try {
      new CIPData(CIPData.Type.OMRON_STRING, "OmronString", "Another String");
      fail("Omron does not support string arrays");
    } catch (Exception e) {
      // normal path - arrays of strings are not supported.
    }

  }

  @Test
  public void testABString() throws Exception {
    final byte[] data = new byte[] { (byte) 0xCE, (byte) 0x0F, 5, 0, 0, 0, 'H', 'e', 'l', 'l', 'o' };
    final CIPData value = new CIPData(CIPData.Type.STRUCT, data, abDeviceInfo);

    assertThat(CIPData.Type.STRUCT, equalTo(value.getType()));

    final String txt = value.getString();
    assertThat(txt, equalTo("Hello"));
    assertThat(value.toString(), equalTo("CIP_STRUCT (0x02A0): STRUCT_STRING (0x0FCE) 'Hello', len 5"));
  }

  @Test
  public void test0OmronString() throws Exception {
    final byte[] data = new byte[] { 5, 0, 'H', 'e', 'l', 'l', 'o' };
    final CIPData value = new CIPData(CIPData.Type.OMRON_STRING, data, omronDeviceInfo);

    assertThat(CIPData.Type.OMRON_STRING, equalTo(value.getType()));

    final String txt = value.getString();
    assertThat(txt, equalTo("Hello"));
    assertThat(value.toString(), equalTo("CIP_OMRON_STRING (0x00D0): \"Hello\""));
  }

}
