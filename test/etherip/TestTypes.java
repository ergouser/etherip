/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * Copyright (c) 2020 ErgoTech Systems, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Test;

import etherip.types.CIPData;

/** @author Kay Kasemir, László Pataki */
public class TestTypes {

  private static final String[] tagList;

  private static final String[] plcs;

  static {
    String allTags = TestSettings.get("all_tags");
    if ( allTags != null ) {
      tagList = allTags.split(",");
    } else {
      tagList = new String[0];
    }
    String allPLCs = TestSettings.get("plcs");
    if ( allPLCs == null ) {
      plcs = new String[] {TestSettings.get("plc")};
    } else {
      plcs = allPLCs.split(",");
    }
  }
  @Before
  public void setup() {
    TestSettings.logAll();
    Logger.getLogger("").setLevel(Level.ALL);
  }

  @Test
  public void testAllTags() throws Exception {
    for ( String plcIP : plcs) {
      try (EtherNetIP plc = new EtherNetIP(plcIP, 0);) {  // ignoring slot
        plc.connectTcp();
        plc.getIdentity(); // always need the identity
        EtherNetIP.throwOnReadError = false;  // we want to know here if the read failed.

        for ( String tag : tagList ) {
          // read the tag. Ignore read fails.  There is no requirement that every tag be in every PLC - that's a different test.
          // If the read succeeds, change the value and write using the type from the read.  Read again and compare.

          CIPData value = plc.readTag(tag);
          if ( value != null ) {
            switch ( value.getType()) {
              case BOOL:{
                int elementCount = value.getElementCount();
                int[] testValues = new int[elementCount];
                CIPData cipData = new CIPData(value.getType(), elementCount);
                for ( int index = 0 ; index < elementCount ; index++ ) {
                  testValues[index] = value.getNumber(index).longValue() == 0 ? 1 : 0;
                  cipData.set(index, testValues[index]);
                }
                plc.writeTag(tag, cipData);
                CIPData readValues = plc.readTag(tag);
                assertEquals(elementCount, readValues.getElementCount());
                for ( int index = 0 ; index < elementCount ; index++ ) {
                  if ( (testValues[index] == 0 && readValues.getNumber(index).intValue() != 0) || (testValues[index] != 0 && readValues.getNumber(index).intValue() == 0) ) {
                    System.out.println ("Will Fail");
                  }
                  boolean test = testValues[index] != 0;
                  boolean incoming = readValues.getNumber(index).intValue() != 0;
                  assertEquals(test, incoming);
                }
              }
              break;
              case SINT:
              case USINT:
              case INT:
              case UINT:
              case DINT:
              case UDINT:
              case ULINT:
              case LINT: {
                int elementCount = value.getElementCount();
                Long[] testValues = new Long[elementCount];
                CIPData cipData = new CIPData(value.getType(), elementCount);
                for ( int index = 0 ; index < elementCount ; index++ ) {
                  int delta;
                  do  {
                    delta = (int)(Math.random() *15); // Always positive so that the unsigneds work
                  } while (delta == value.getNumber(index).longValue() );
                  testValues[index] = new Long(delta);
                  cipData.set(index, testValues[index]);
                }
                plc.writeTag(tag, cipData);
                CIPData readValues = plc.readTag(tag);
                assertEquals(elementCount, readValues.getElementCount());
                for ( int index = 0 ; index < elementCount ; index++ ) {
                  if (testValues[index].longValue() != readValues.getNumber(index).longValue() ) {
                    System.out.println ("Will Fail");
                  }
                  assertEquals(testValues[index].longValue(), readValues.getNumber(index).longValue());
                }
              }
              break;
              case LREAL: 
              case REAL: {
                double delta;
                do  {
                  delta = Math.random() *10 - 5; // non-zero change
                } while (delta == 0 );
                int elementCount = value.getElementCount();
                Double[] testValues = new Double[elementCount];
                CIPData cipData = new CIPData(value.getType(), elementCount);
                for ( int index = 0 ; index < elementCount ; index++ ) {
                  testValues[index] = new Double(value.getNumber(index).doubleValue() + delta);
                  cipData.set(index, testValues[index]);
                }
                plc.writeTag(tag, cipData);
                CIPData readValues = plc.readTag(tag);
                assertEquals(elementCount, readValues.getElementCount());
                for ( int index = 0 ; index < elementCount ; index++ ) {
                  assertEquals(testValues[index].doubleValue(), readValues.getNumber(index).doubleValue(), 0.00001);
                }
              }
              break;
              case BITS:
                break;  //bit are not written correctly...
              case STRUCT: 
                if ( value.getEncodedStructureType() == CIPData.Type.STRUCT_STRING ) {
                  // nothing currently...
                }
                break;
              case OMRON_STRING: {
                String valueString = value.getString();
                if ( valueString.length() > 1 ) {
                  int delta;
                  do {
                    delta = (int)(Math.random() *45) + 30; // non-zero change
                  } while (delta == valueString.charAt(valueString.length()-1));
                  valueString = valueString.substring(0,valueString.length()-1) + Character.toString((char)delta);
                } else {
                  valueString += (int)(Math.random() *45) + Character.toString((char)30);
                }
                CIPData writeString = new CIPData(CIPData.Type.OMRON_STRING, valueString);
                plc.writeTag(tag, writeString);
                CIPData readValues = plc.readTag(tag);
                assertEquals(1, readValues.getElementCount());
                assertEquals(valueString, readValues.getString());
              }
              break;
              default:
                fail("Unknown Tag Type");

            }


          }
        }
      }
    }
  }

}
