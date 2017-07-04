/*
 * Copyright (C) 2017 noti0na1 <i@notnl.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.notnl.hfsn;

import java.io.File;

/**
 *
 * @author noti0na1 <i@notnl.com>
 */
public class Pages {

    //private static final Pattern ALLOWED_FILE_NAME = Pattern.compile("[A-Za-z0-9][-_A-Za-z0-9\\.]*");
    public static StringBuilder getDirectory(File dir) {
        String dirPath = dir.getPath();
        StringBuilder buf = new StringBuilder()
                .append("<!DOCTYPE html>\r\n")
                .append("<html><head><title>")
                .append("Listing of: ")
                .append(dirPath)
                .append("</title></head><body>\r\n")
                .append("<h3>\t Listing of: ")
                .append(dirPath)
                .append("</h3>\r\n")
                .append("<hr />\r\n")
                .append("<ul>\r\n")
                .append("<li><a href=\"../\">..</a></li>\r\n");
        //String fileCode = SystemPropertyUtil.get("file.encoding");
        for (File f : dir.listFiles()) {
            if (f.isHidden() || !f.canRead()) {
                continue;
            }
            //String name = new String(f.getName().getBytes(), fileCode);
            String name = f.getName();
//            if (!ALLOWED_FILE_NAME.matcher(name).matches()) {
//                continue;
//            }
            // System.out.println(name);
            buf.append("<li><a href=\"")
                    .append(name)
                    .append("\">")
                    .append(name)
                    .append("</a></li>\r\n");
        }
        buf.append("</ul>\r\n</body></html>\r\n");
        return buf;
    }

    public static StringBuilder getPlayer() {
        return null;
    }
}
