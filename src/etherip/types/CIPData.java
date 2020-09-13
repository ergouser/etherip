/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.types;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import etherip.data.Identity;
import etherip.protocol.Connection;

/**
 * ControlNet data types
 * <p>
 * Spec. 5 p.3
 * <p>
 * Raw CIP data is kept in a <code>byte[]</code>. This class can decode the data or manipulate it.
 * <p>
 * Note that all operations that 'set' the value require that the CIPData already holds the respective type. For
 * example, setting a CIPData of type REAL to an integer value will still result in a REAL, not change the type to INT.
 * Setting a CIPData of type INT to a floating point value will truncate the floating point to an integer, since the
 * CIPData remains an INT. Only CIPData with a string-containing STRUCT can be set to a string.
 *
 * @author Kay Kasemir
 */
@SuppressWarnings("nls")
final public class CIPData {
  public static enum Type {
    BOOL(0x00C1, 1), 
    SINT(0x00C2, 1), 
    INT(0x00C3, 2), 
    DINT(0x00C4, 4), 
    LINT(0x00C5, 8), // Omron
    USINT(0x00C6, 1), // Omron
    UINT(0x00C7, 2), // Omron
    UDINT(0x00C8, 4), // Omron
    ULINT(0x00C9, 8), // Omron
    REAL(0x00CA, 4), 
    LREAL(0x00CB, 8),  // Omron
    BITS(0x00D3, 4),
    // Order of enums matter: BITS is the last numeric type (not-string) 

    OMRON_STRING(0xD0, 0), // this must be before the "STRUCT" definition

    // Order of enums matter all non-structures must be above this entry
    STRUCT(0x02A0, 0),  // this must be the first of the structures.   > ordinal() of this is used to determine if the type is a structure

    /**
     * Experimental: The ENET doc just shows several structures for TIMER, COUNTER, CONTROL and indicates that the
     * T_CIP_STRUCT = 0x02A0 is followed by two more bytes, shown as "?? ??". Looks like for strings, those are always
     * 0x0FCE, followed by DINT length, characters and more zeroes
     */
    STRUCT_STRING(0x0FCE, 0), 
    STRUCT_COUNTER(0x0F82, 0), 
    STRUCT_TIMER(0x0F83, 0), 
    STRUCT_ALARM(0x0F8B, 0);

    final private short code;

    final private int element_size;

    final private static Map<Short, Type> reverse;

    static {
      reverse = new HashMap<>();
      for (final Type t : EnumSet.allOf(Type.class)) {
        reverse.put(t.code, t);
      }
    }

    public static Type forCode(final short code) throws Exception {
      final Type t = reverse.get(code);
      if (reverse == null) {
        throw new Exception("Unknown CIP type code 0x" + Integer.toHexString(code));
      }
      return t;
    }

    private Type(final int code, final int element_size) {
      this.code = (short) code;
      this.element_size = element_size;
    }

    @Override
    final public String toString() {
      return this.name() + String.format(" (0x%04X)", this.code);
    }
  };

  /** Data type */
  final private Type type;

  /** Number of elements (i.e. number of array elements, not bytes) */
  final private short elements;

  /** Raw data, not including type code or element count */
  final private ByteBuffer data;

  /** The Identity. Primarily the vendor id. */
  protected Identity identity;

  /** The encoded type for a structure (null for non-structure types). */
  private Type encodedStructureType;

  /**
   * Initialize empty CIP data
   *
   * @param type
   *          Data {@link Type}
   * @param elements
   *          Number of elements
   * @throws Exception
   *           when type is not handled
   */
  public CIPData(final Type type, final int elements) throws Exception {
    this(type, elements, Identity.LAST_DEVICE_INFO_FROM_PLC);
  }

  /**
   * Initialize empty CIP data
   *
   * @param type
   *          Data {@link Type}
   * @param elements
   *          Number of elements
   * @throws Exception
   *           when type is not handled
   */
  public CIPData(final Type type, final int elements, Identity identity) throws Exception {
    this.identity = identity;
    switch (type) {
      case BOOL:
        // booleans are different on AB and Omron. On AB they are 1 byte, on Omron 2
        int elementSize = type.element_size;
        if (identity.getVendorId() == Identity.VENDOR_OMRON) {
          elementSize = 2;
        }
        this.data = ByteBuffer.allocate(elementSize * elements);
        this.data.order(Connection.BYTE_ORDER);
        this.type = type;
        this.elements = (short) elements;
        break;
      case SINT:
      case INT:
      case DINT:
      case LINT:
      case USINT:
      case UINT:
      case UDINT:
      case ULINT:
      case BITS:
      case REAL:
      case LREAL:
        this.data = ByteBuffer.allocate(type.element_size * elements);
        this.data.order(Connection.BYTE_ORDER);
        this.type = type;
        this.elements = (short) elements;
        break;
      default:
        throw new Exception("Type " + type + " not handled");
    }
  }

  /**
   * Create a CIPData from the strings provided.
   * 
   * AB/Rockwell supports string array writes.  NX/NJ does not and so values must always be size 1.
   * 
   * @throws Exception For AB/Rockwell the type must be Type.STRUCT_STRING.  For NX/NJ, Type.OMRON_STRING,  All other types will throw.  Will throw for an 
   * Omron string is the the length of values is not one.
   * 
   */
  public CIPData(final Type type, final String... values) throws Exception, IndexOutOfBoundsException {
    if ( type == Type.STRUCT_STRING ) {
      encodedStructureType = Type.STRUCT_STRING;
      //final byte[] data = new byte[] { (byte)0xCE, (byte)0x0F, 5, 0, 0, 0, 'H', 'e', 'l', 'l', 'o' };
      // The byte buffer must be allocated to the total size of the strings + 4 bytes per string for the length
      int totalStringLength = 0;
      for ( String value : values ) {
        int stringLength = value.getBytes("UTF-8").length;
        totalStringLength += stringLength + 4;
        if ( stringLength % 2 != 0 ) {
          totalStringLength++;  // odd length strings must be padded.
        }
      }
      data = ByteBuffer.allocate(totalStringLength); // the length is encoded in 4 bytes
      this.data.order(Connection.BYTE_ORDER);
      for ( String value : values ) {
        int stringLength = value.getBytes("UTF-8").length;
        if ( stringLength %2 != 0 ) {
          stringLength++;
        }
        data.putInt(stringLength);
        data.put(value.getBytes("UTF-8"));  //UTF-8 support
        if ( stringLength % 2 != 0 ) {
          data.put((byte)0);
        }
      }
      elements = (short)values.length;
      this.type = Type.STRUCT;
    } else if ( type == Type.OMRON_STRING ) {
      if ( values.length != 1 ) {
        // don't know how to handle arrays - it's not clear the the NJ/NX PLCs support it
        throw new Exception("Omron String Array are not supported.");
      }
      // single strin.  There are two length bytes and then the string.  The string can be an odd length (does not need to be padded).
      //new byte[] ( L0, L1, 'C0', 'C1' ... , 'CL'); // L0, L1 are the length.
      short stringLength = (short)values[0].getBytes("UTF-8").length;
      data = ByteBuffer.allocate(stringLength +2); // the length is encoded in 2 bytes
      this.data.order(Connection.BYTE_ORDER);
      data.putShort(stringLength);
      data.put(values[0].getBytes("UTF-8"));
      elements = 1;
      this.type = Type.OMRON_STRING;
    } else {
      throw new Exception("Type " + type + " not handled");
    }
  }

  /**
   * Initialize.  This uses the default device info (DeviceInfo.LAST_DEVICE_INFO_FROM_PLC)
   * to maintain backwards compatibility.
   *
   * @param type Data type
   * @param dattype Bytes that contain the raw CIP data
   * @throws Exception when data is invalid
   */
  public CIPData(final Type type, final byte[] data) throws Exception {
    this(type, data, Identity.LAST_DEVICE_INFO_FROM_PLC);
  }

  /**
   * Initialize.  This populates the CIPData from the incoming network packet. Not normally called by user code.
   *
   * @param type  Data type
   * @param data  Bytes that contain the raw CIP data
   * @throws Exception  when data is invalid
   */
  public CIPData(final Type type, final byte[] data, final Identity identity) throws Exception {
    this.identity = identity;
    this.type = type;
    this.data = ByteBuffer.allocate(data.length);
    this.data.order(Connection.BYTE_ORDER);
    this.data.put(data);
    this.elements = determineElementCount();
  }

  /** @return Number of elements */
  final private short determineElementCount() throws Exception {
    switch (this.type) {
      case BOOL:
        int elementSize = type.element_size;
        if (identity.getVendorId() == Identity.VENDOR_OMRON) {
          elementSize = 2;
        }
        return (short) (data.capacity() / elementSize);
      case SINT:
      case INT:
      case DINT:
      case LINT:
      case USINT:
      case UINT:
      case UDINT:
      case ULINT:
      case REAL:
      case LREAL:
        return (short) (this.data.capacity() / this.type.element_size);
      case BITS:
        // if you ask for a Bool array, you get back the correct number of bits
        // that is, if the Bool array is 32, you get back 32 bits.  I don't know what happens if the array is 31
        return (short)(this.data.capacity() *8);
        //return 1;  // there's no way to know the size of a bit array.  Assume and return 1 unless told otherwise.
      case OMRON_STRING: {
        return 1; // single element
      }
      case STRUCT: {
        encodedStructureType = Type.forCode(data.getShort(0));
        if (encodedStructureType == Type.STRUCT_STRING) {
          // for an array each string is returned as the full, declared length
          // however, the length description of the message only the actually used string
          // length is provided. This means that to read string arrays you need to know
          // how many you requested to know how to decode the response.
          // A0 02 CE 0F - ................
          // 0030 - 0A 00 00 00 33 31 33 31 33 31 33 31 33 31 00 00 - ....3131313131..
          // 0040 - 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 - ................
          // 0050 - 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 - ................
          // 0060 - 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 - ................
          // 0070 - 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 - ................
          // 0080 - 00 00 00 00 00 00 00 00 0A 00 00 00 33 32 33 32 - ............3232
          // 0090 - 33 32 33 32 33 32 00 00 00 00 00 00 00 00 00 00 - 323232..........
          // 00A0 - 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 - ................
          // 00B0 - 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 - ................
          // 00C0 - 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 - ................
          // 00D0 - 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 - ................
          // 00E0 - 0A 00 00 00 33 33 33 33 33 33 33 33 33 33 00 00 - ....3333333333..
          // 00F0 - 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 - ................
          // 0100 - 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 - ................
          // 0110 - 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 - ................
          // 0120 - 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 - ................
          // 0130 - 00 00 00 00 00 00 00 00
          return -1;
        } else if (encodedStructureType == Type.STRUCT_COUNTER || encodedStructureType == Type.STRUCT_TIMER) {
          return (short) ((data.capacity() - 2) / 12);
          // Counter cc 00 00 00 a0 02 82 0f 00 00 00 80 0c 00 00 00 50 00 00 00
          // (Pre is 12 0c 00 00 00- byte13-17 - and ACC is 80 - byte17 0x50 this is a 32 bit value (DINT) 50 00 00 00
          // byte before PRE is the status byte.)

          // PRE is bytes 6-9 of the data.hb
          // ACC is bytes 10-13

          // timer CC 00 00 00 A0 02 83 0F 00 00 00 00 00 00 00 00 00 00 00 00 - presumably the same as counters...
        } else if (encodedStructureType == Type.STRUCT_ALARM) {
          return data.getShort(2);
          // Alarms no idea of the format... CC 00 00 00 A0 02 8B 0F - ......h.........
          // 0030 - 01 00 00 00 00 00 00 00 FF FF 7F 7F FF FF 7F 7F - ................
          // 0040 - FF FF 7F FF FF FF 7F FF 00 00 00 00 00 00 00 00 - ................
          // 0050 - 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 - ................
          // 0060 - 00 00 00 00 04 00 00 00 00 00 00 00 00 00 00 00 - ................
          // 0070 - 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 - ................
          // 0080 - 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
        } else {
          throw new Exception("Structure elements of type " + type + " not handled");
        }
      }
      default:
        throw new Exception("Type " + this.type + " not handled");
    }
  }

  /** @return CIP data type */
  final public Type getType() {
    return this.type;
  }

  /**
   * @return the encodedStructureType
   */
  public Type getEncodedStructureType() {
    return encodedStructureType;
  }

  /**
   * @return Number of elements (numbers in array). Always 1 for String
   */
  final public int getElementCount() {
    return this.elements;
  }

  /** @return <code>true</code> if data type is numeric, <code>false</code> for string */
  final public boolean isNumeric() {
    return this.type.ordinal() <= Type.BITS.ordinal();
  }

  /**
   * Read CIP data as number
   *
   * @param index
   *          Element index 0, 1, ...
   * @return Numeric value of requested element
   * @throws Exception
   *           on error, if data is not numeric
   * @throws IndexOutOfBoundsException
   *           if index is invalid
   */
  final synchronized public Number getNumber(final int index) throws Exception, IndexOutOfBoundsException {
    switch (this.type) {
      case BOOL:
      case SINT:
        return new Byte(this.data.get(this.type.element_size * index));
      case USINT:
        return new Short((short)(data.get(type.element_size * index) & 0xFF));
      case INT:
        return new Short(this.data.getShort(this.type.element_size * index));
      case UINT:
        return new Integer((data.getShort(type.element_size * index)) & 0xFFFF);
      case DINT:
        return new Integer(data.getInt(type.element_size * index));
      case UDINT:
        return new Long((data.getInt(type.element_size * index) & 0xFFFFFFFFL));
      case LINT:
      case ULINT:  // this is handles as signed.  
        return new Long(data.getLong(type.element_size * index));
      case BITS: {
        // bits are packed into a byte
        int unpacked = data.getInt(index/(type.element_size*8));
        int bits = (byte)(unpacked>>(index%(type.element_size*8)));
        return new Integer(bits);
      }
      //return new Integer(this.data.getInt(this.type.element_size * index));
      case REAL:
        return new Float(this.data.getFloat(this.type.element_size * index));
      case LREAL:
        return new Double(this.data.getDouble(this.type.element_size * index));
      default:
        throw new Exception("Cannot retrieve Number from " + this.type);
    }
  }

  /**
   * Read CIP data as string
   *
   * @return {@link String}
   * @throws Exception if data does not contain a string
   */
  public String getOmronString() throws Exception {
    if (type != Type.OMRON_STRING) {
      throw new Exception("Type " + type + " does not contain an Omron string");
    }
    final int len = data.getShort(0);
    final byte[] chars = new byte[len];
    for (int i = 0; i < len; ++i) {
      chars[i] = data.get(2 + i);
    }
    return new String(chars, Charset.forName("UTF-8"));  // Omron Strings are in UTF-8 format
  }

  /**
   * Read CIP data as string
   *
   * @return {@link String}
   * @throws Exception if data does not contain a string
   */
  final synchronized public String getString() throws Exception {
    if (type == Type.OMRON_STRING) {
      return getOmronString();
    }
    if (this.type != Type.STRUCT) {
      throw new Exception("Type " + this.type + " does not contain string");
    }
    final short code = this.data.getShort(0);
    encodedStructureType = Type.forCode(code);
    if (encodedStructureType != Type.STRUCT_STRING) {
      throw new Exception("No string, structure element is of type " + this.type);
    }
    final int len = this.data.getInt(2);
    final byte[] chars = new byte[len];
    for (int i = 0; i < len; ++i) {
      chars[i] = this.data.get(6 + i);
    }
    return new String(chars, Charset.forName("UTF-8"));
  }

  /**
   * Read CIP data as a string array.  The returned data does not provide any indication of the size of the string
   * array, so that must be provided as an argument to the method.
   *
   * @param arraySize the size of the string array
   * @return {@link String}
   * @throws Exception if data does not contain a string
   */
  public String[] getStrings(int arraySize) throws Exception {
    // There is no support for arrays of Omron Strings.  It's not clear the the NJ/NX support that.
    if (type != Type.STRUCT) {
      throw new Exception("Type " + type + " does not contain string");
    }
    final short code = data.getShort(0);
    encodedStructureType = Type.forCode(code);
    if (encodedStructureType != Type.STRUCT_STRING) {
      throw new Exception("Not strings. Structure element is of type " + type);
    }
    // the structure is, for example, this.
    //          CE 0F - ................
    //          0030 - 0A 00 00 00 33 31 33 31 33 31 33 31 33 31 00 00 - ....3131313131..
    //          0040 - 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 - ................
    //          0050 - 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 - ................
    //          0060 - 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 - ................
    //          0070 - 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 - ................
    //          0080 - 00 00 00 00 00 00 00 00 0A 00 00 00 33 32 33 32 - ............3232
    //          0090 - 33 32 33 32 33 32 00 00 00 00 00 00 00 00 00 00 - 323232..........
    //          00A0 - 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 - ................
    //          00B0 - 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 - ................
    //          00C0 - 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 - ................
    //          00D0 - 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 - ................
    //          00E0 - 0A 00 00 00 33 33 33 33 33 33 33 33 33 33 00 00 - ....3333333333..
    //          00F0 - 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 - ................
    //          0100 - 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 - ................
    //          0110 - 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 - ................
    //          0120 - 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 - ................
    //          0130 - 00 00 00 00 00 00 00 00 

    // after discarding the first two bytes, the remaining data should be exactly divisible by the arraySize
    if ( ((data.capacity()-2) % arraySize) != 0 ) {
      throw new Exception("The data does not seem to contain an array of the provided size " + arraySize);
    }
    String[] toReturn = new String[arraySize];
    int sizeOfEachStringWithCountBytes = ((data.capacity()-2) / arraySize);
    int index = 2;
    for ( int counter = 0 ; counter < arraySize ; counter++ ) {
      int len = data.getInt(index);  // even though the entire string is encoded, only the "len" characters are significant.
      byte[] chars = new byte[len];
      for (int length = 0; length < len; length++) {
        chars[length] = data.get(index+4 + length);
      }
      toReturn[counter] = new String(chars);  // only 7-bit ASCII support (possibly 8-bit but we're not specifying the encoding for that).
      index += sizeOfEachStringWithCountBytes;
    }
    return toReturn;
  }


  /**
   * Set CIP data
   *
   * @param index
   *          Element index 0, 1, ...
   * @param value
   *          Numeric value to write to that element
   * @throws Exception
   *           on invalid data type
   * @throws IndexOutOfBoundsException
   *           if index is invalid
   */
  final synchronized public void set(final int index, final Number value) throws Exception, IndexOutOfBoundsException {
    switch (this.type) {
      case BOOL:
        if (identity.getVendorId() == Identity.VENDOR_OMRON) {
          if ( value.shortValue() != 0 ) {
            data.putShort(type.element_size * index, (short)1);
          } else {
            data.putShort(type.element_size * index, (short)0);
          }          
        } else {
          if ( value.byteValue() != 0 ) {
            data.put(type.element_size * index, (byte)0xFF);
          } else {
            data.put(type.element_size * index, (byte)0);
          }
        }
        break;
      case SINT:
        this.data.put(this.type.element_size * index, value.byteValue());
        break;
      case USINT:
        data.put(type.element_size * index, value.byteValue());
        break;
      case INT:
        this.data.putShort(this.type.element_size * index, value.shortValue());
        break;
      case UINT:
        data.putShort(type.element_size * index, value.shortValue());
        break;
      case DINT:
        data.putInt(type.element_size * index, value.intValue());
        break;
      case UDINT:
        data.putInt(type.element_size * index, value.intValue());
        break;
      case ULINT:
      case LINT:
        data.putLong(type.element_size * index, value.longValue());
        break;
      case BITS:
        int bitValues = data.getInt(index/(type.element_size*8));
        if ( value.intValue() != 0 ) { // any non-zero value means set...
          bitValues |= (1<<(index%(type.element_size*8)));
        } else {
          bitValues &= ~(1<<(index%(type.element_size*8)));
        }
        data.putInt(index/(type.element_size*8), bitValues);
        break;
        //        this.data.putInt(this.type.element_size * index, value.intValue());
        //        break;
      case REAL:
        this.data.putFloat(this.type.element_size * index, value.floatValue());
        break;
      case LREAL:
        this.data.putDouble(this.type.element_size * index, value.doubleValue());
        break;
      default:
        throw new Exception("Cannot set type " + this.type + " to a number");
    }
  }

  /**
   * Write CIP data as string.
   *
   * @param text
   *          {@link String}
   * @throws Exception
   *           if data does not contain a string
   */
  // I see no way to create a CIPData of type STRUCT .  The public constructors, eg
  // public CIPData(final Type type, final int elements) throws Exception {
  // do not support strings.  I assume that this is therefore dead code.
  // The length of the string is required to create the (final) data instance and that's not provided in the above 
  // constructor.
  // The new constructor:
  //   public CIPData(final Type type, final String... values) throws Exception, IndexOutOfBoundsException {
  // allows creation of a CIP with a string.
  final synchronized public void setString(final String text) throws Exception {
    if (this.type != Type.STRUCT) {
      throw new Exception("Type " + this.type + " does not contain string");
    }
    this.data.putShort(0, Type.STRUCT_STRING.code);

    // Try to fit the text,
    // but limit it to size of buffer,
    // starting at the offset for the text
    // (2 byte STRUCT_STRING, 4 byte length)
    // and allow for the final '\0' byte
    final int len = Math.min(text.length(), this.data.capacity() - 6 - 1);
    this.data.putInt(2, len);

    final byte[] chars = text.getBytes();
    for (int i = 0; i < len; ++i) {
      this.data.put(6 + i, chars[i]);
    }
    this.data.put(6 + len, (byte) 0);
  }

  /** @return size if bytes of the encoded data */
  final public int getEncodedSize() {
    // Type, Elements, raw data
    int encodedSize = 2 + 2 + data.capacity();
    if ( type.ordinal() > Type.STRUCT.ordinal()) {
      // Structure encoding needs two extra bytes.
      // the structure is encoded as 0x02A0
      // then the type of the structure must be encoded, for example
      // a  STRUCT_STRING(0x0FCE)
      encodedSize += 6; //2;
    }
    return encodedSize;
  }

  /**
   * Encode CIP data bytes into buffer
   *
   * @param buf
   *          {@link ByteBuffer} where data should be placed
   * @throws Exception
   *           on error
   */
  final synchronized public void encode(final ByteBuffer buf) throws Exception {
    // required that STRUCT be before any actual structures.
    if ( type.ordinal() < Type.STRUCT.ordinal() ) {
      buf.putShort(type.code);
      buf.putShort(elements);
      buf.put(data.array());
    } else {
      buf.putShort(Type.STRUCT.code);
      this.data.clear();
      Type.forCode(this.data.getShort());
      buf.putShort((short)encodedStructureType.code);  // not sure what other structures work beside STRUCT_STRING
      // The data buffer contains the string as _read_:
      // STRUCT, STRUCT_STRING, length, chars.
      // It needs to be written as
      // STRUCT, STRUCT_STRING, _elements_, length, chars.
      buf.putShort(this.elements);
      buf.putInt(0);
      //buf.putShort((short)0);  
      // Copy length, chars from data into buf
      buf.put(this.data.array());
    }

    //STRUCT(0x02A0, 0),
    //STRUCT_STRING(0x0FCE, 0), 
    //          CE 0F - ................
    //          0030 - 0A 00 00 00 33 31 33 31 33 31 33 31 33 31 00 00 - ....3131313131..

    //    buf.putShort(Type.STRUCT.code);
    //    buf.putShort(encodedStructureType.code);  // not sure what other structures work beside STRUCT_STRING
    //    buf.putShort(elements);
    //    //buf.putInt(0);
    //    buf.putShort((short)0);  // int?
    //    buf.put(data.array());

    //    buf.putShort(this.type.code);
    //    // STRUCT already contains structure detail, elements etc.
    //    // For other types, add the element count
    //    if (this.type == Type.STRUCT) {
    //      this.data.clear();
    //      final short struct_detail = this.data.getShort();
    //      if (struct_detail != Type.STRUCT_STRING.code) {
    //        throw new Exception("Can only encode STRUCT_STRING, got 0x" + Integer.toHexString(struct_detail));
    //      }
    //      // The data buffer contains the string as _read_:
    //      // STRUCT, STRUCT_STRING, length, chars.
    //      // It needs to be written as
    //      // STRUCT, STRUCT_STRING, _elements_, length, chars.
    //      buf.putShort(struct_detail);
    //      buf.putShort(this.elements);
    //      // Copy length, chars from data into buf
    //      buf.put(this.data);
    //    } else {
    //      buf.putShort(this.elements);
    //      buf.put(this.data.array());
    //    }

    //    buf.putShort(Type.STRUCT.code);
    //    buf.putShort(type.code);
    //    buf.putShort(elements);
    //    buf.putInt(0);
    //    buf.put(data.array());

  }


  /** @return String representation for debugging */
  @Override
  final synchronized public String toString() {
    final StringBuilder result = new StringBuilder();
    result.append("CIP_").append(this.type).append(": ");
    final ByteBuffer buf = this.data.asReadOnlyBuffer();
    buf.order(this.data.order());
    buf.clear();
    switch (this.type) {
      case BOOL: {
        final String[] values = new String[elements];
        for (int i = 0; i < elements; ++i) {
          values[i] = buf.get() != 0 ? "TRUE" : "FALSE";
        }
        result.append(Arrays.toString(values));
        break;
      }
      case SINT: {
        final byte[] values = new byte[this.elements];
        buf.get(values);
        result.append(Arrays.toString(values));
        break;
      }
      case USINT: {
        final short[] values = new short[elements];
        for (int i = 0; i < elements; ++i)
          values[i] = (short)(buf.get() & 0xFF);
        result.append(Arrays.toString(values));
        break;
      }
      case INT: {
        final short[] values = new short[this.elements];
        for (int i = 0; i < this.elements; ++i) {
          values[i] = buf.getShort();
        }
        result.append(Arrays.toString(values));
        break;
      }
      case UINT: {
        final int[] values = new int[elements];
        for (int i = 0; i < elements; ++i)
          values[i] = buf.getShort() & 0xFFFF;
        result.append(Arrays.toString(values));
        break;
      }
      case DINT: {
        final int[] values = new int[elements];
        for (int i = 0; i < elements; ++i)
          values[i] = buf.getInt();
        result.append(Arrays.toString(values));
        break;
      }
      case UDINT: {
        final long[] values = new long[elements];
        for (int i = 0; i < elements; ++i)
          values[i] = buf.getInt() & 0xFFFFFFFFL;
        result.append(Arrays.toString(values));
        break;
      }
      case ULINT: // not correctly handled - treated as signed
      case LINT: {
        final long[] values = new long[elements];
        for (int i = 0; i < elements; ++i)
          values[i] = buf.getLong();
        result.append(Arrays.toString(values));
        break;
      }
      case BITS: {
        final int[] values = new int[elements]; // new int[elements%(type.element_size*8)+1];
        for (int i = 0; i < elements/8; ++i) {
          byte value = buf.get();
          for ( int bitposn = 0 ; bitposn < 8 ; bitposn++ ) {
            values[i + bitposn] = (value >> bitposn)  & 0x1;
          }
        }
        result.append(Arrays.toString(values));
        break;
      }
      case REAL: {
        final float[] values = new float[this.elements];
        for (int i = 0; i < this.elements; ++i) {
          values[i] = buf.getFloat();
        }
        result.append(Arrays.toString(values));
        break;
      }
      case OMRON_STRING: {
        final int len = buf.getShort(0);
        final byte[] chars = new byte[len];
        for (int i = 0; i < len; ++i) {
          chars[i] = buf.get(2 + i);
        }
        result.append('"');
        result.append(new String(chars, Charset.forName("UTF-8")));
        result.append('"');
        break;
      }
      case STRUCT_STRING: {
        result.append(Type.STRUCT_STRING).append(" ");
        final int len = buf.getInt();
        final byte[] chars = new byte[len];
        buf.get(chars);
        final String value = new String(chars, Charset.forName("UTF-8"));
        result.append("'").append(value).append("', len " + len);
        break;
      }
      case STRUCT: {
        final short code = buf.getShort();
        final Type el_type;
        try {
          el_type = Type.forCode(code);
        } catch (final Exception ex) {
          result.append("Structure element with type code 0x" + Integer.toHexString(code));
          break;
        }
        if (el_type == Type.STRUCT_STRING) {
          result.append(Type.STRUCT_STRING).append(" ");
          final int len = buf.getInt();
          final byte[] chars = new byte[len];
          buf.get(chars);
          final String value = new String(chars);
          result.append("'").append(value).append("', len " + len);
        } else {
          result.append("Structure element of type " + this.type);
        }
        break;
      }
      default:
        result.append("Unknown Type " + this.type);
    }
    return result.toString();
  }
}
