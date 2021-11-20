/*
 * $Id$
 */
package org.codehaus.mojo.antlr.options;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/**
 * A Grammar parameter.
 * 
 * @version $Revision$ $Date$
 */
public class Grammar implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private String glib;

    /**
     * Get Colon separated or semicolon separated supergrammar file names.
     */
    public String getGlib() {
        return this.glib;
    }

    /**
     * Set Colon separated or semicolon separated supergrammar file names.
     * 
     * @param glib
     */
    public void setGlib(String glib) {
        this.glib = glib;
    }

    /**
     * Get The grammar file name.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Set The grammar file name.
     * 
     * @param name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Method equals
     * 
     * @param other
     */
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof Grammar)) {
            return false;
        }

        Grammar that = (Grammar) other;
        boolean result = true;
        result = result && (getName() == null ? that.getName() == null : getName().equals(that.getName()));
        result = result && (getGlib() == null ? that.getGlib() == null : getGlib().equals(that.getGlib()));
        return result;
    } // -- boolean equals(Object)

    /**
     * Method hashCode
     */
    public int hashCode() {
        int result = 17;
        long tmp;
        result = 37 * result + (name != null ? name.hashCode() : 0);
        result = 37 * result + (glib != null ? glib.hashCode() : 0);
        return result;
    } // -- int hashCode()

    /**
     * Method toString
     */
    public java.lang.String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("name = '");
        buf.append(getName() + "'");
        buf.append("\n");
        buf.append("glib = '");
        buf.append(getGlib() + "'");
        return buf.toString();
    } // -- java.lang.String toString()

    private String modelEncoding = "UTF-8";

    public void setModelEncoding(String modelEncoding) {
        this.modelEncoding = modelEncoding;
    }

    public String getModelEncoding() {
        return modelEncoding;
    }
}
