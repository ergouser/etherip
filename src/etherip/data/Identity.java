/*******************************************************************************
 * Copyright (c) 2017 NETvisor Ltd.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.data;

import java.util.Arrays;

/**
 * Identity Object.
 * <p>
 * Status parameter is not decoded.<br>
 * Example for decoding:<br>
 * Status: 0x0060<br>
 * .... .... .... ...0 = Owned: 0<br>
 * .... .... .... .0.. = Configured: 0<br>
 * .... .... 0110 .... = Extended Device Status: 0x6<br>
 * .... ...0 .... .... = Minor Recoverable Fault: 0<br>
 * .... ..0. .... .... = Minor Unrecoverable Fault: 0<br>
 * .... .0.. .... .... = Major Recoverable Fault: 0<br>
 * .... 0... .... .... = Major Unrecoverable Fault: 0<br>
 * 0000 .... .... .... = Extended Device Status 2: 0x0<br>
 *
 * @see CIP_VOL1-3.3: 5-2 Identity Object
 * @author László Pataki
 */
public class Identity
{
  /** Vendor ID for Omron */
  public static final short VENDOR_OMRON = 0x2F;
  
  /** Vendor ID for AB/Rockwell */  // empirically from ControlLogix - Device vendor=0x1, device_type=0xC, revision=0x302, serial=0xA59D, name='1756-ENBT/A'
  public static final short VENDOR_AB = 0x01;
  
  /** The "default" Identity.  This is set to a dummy, default, AB version and will be updated to match the Identity last created 
   * (presumably the last read from the PLC).  This maintains backwards compatibility and will also allow an existing codebase to 
   * connect to an Omron NX/NJ PLC without changes.
   */
  // this may never be null
  public static Identity LAST_DEVICE_INFO_FROM_PLC;
  
  /** Create a default identity. */
  static {
    LAST_DEVICE_INFO_FROM_PLC = new Identity();
    LAST_DEVICE_INFO_FROM_PLC.setVendorId(VENDOR_AB);
    LAST_DEVICE_INFO_FROM_PLC.setProductCode((short)0xFF);
    LAST_DEVICE_INFO_FROM_PLC.setRevision(new Integer[] {0xFF});
    LAST_DEVICE_INFO_FROM_PLC.setSerialNumber("Compatibility");
    LAST_DEVICE_INFO_FROM_PLC.setStatus("0");
    LAST_DEVICE_INFO_FROM_PLC.setProductName("ABCompatibilityDevice");
  }

    private Integer vendorId, deviceType, productCode;

    private Integer[] revision;

    private String productName, serialNumber, status;

    public Identity()
    {
        this.vendorId = null;
        this.deviceType = null;
        this.productCode = null;
        this.revision = new Integer[] { null, null };
        this.status = null;
        this.serialNumber = null;
        this.productName = null;
        LAST_DEVICE_INFO_FROM_PLC = this;
    }

    public int getVendorId()
    {
        return this.vendorId;
    }

    public void setVendorId(final int vendorId)
    {
        this.vendorId = vendorId;
    }

    public int getDeviceTypeRaw()
    {
        return this.deviceType;
    }

    public void setDeviceType(final int deviceType)
    {
        this.deviceType = deviceType;
    }

    public int getProductCode()
    {
        return this.productCode;
    }

    public void setProductCode(final int productCode)
    {
        this.productCode = productCode;
    }

    public Integer[] getRevision()
    {
        return this.revision;
    }

    public void setRevision(final Integer[] revision)
    {
        this.revision = revision;
    }

    public int getMajorRevision()
    {
        return this.revision[0];
    }

    public int getMinorRevision()
    {
        return this.revision[1];
    }

    public String getStatusRaw()
    {
        return this.status;
    }

    public void setStatus(final String status)
    {
        this.status = status;
    }

    public String getSerialNumberRaw()
    {
        return this.serialNumber;
    }

    public void setSerialNumber(final String serialNumber)
    {
        this.serialNumber = serialNumber;
    }

    public String getProductName()
    {
        return this.productName;
    }

    public void setProductName(final String productName)
    {
        this.productName = productName;
    }

    @Override
    public String toString()
    {
        return "Identity [vendorId=" + Integer.toHexString(this.vendorId) + ", deviceType="
                + this.deviceType + ", productCode=" + this.productCode
                + ", revision=" + Arrays.toString(this.revision)
                + ", productName=" + this.productName + ", serialNumber="
                + this.serialNumber + ", status=" + this.status + "]";
    }

}
