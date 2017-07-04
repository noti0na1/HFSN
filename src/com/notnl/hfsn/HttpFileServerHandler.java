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

import com.notnl.hfsn.Config;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.StringUtil;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.activation.MimetypesFileTypeMap;

/**
 *
 * @author noti0na1 <i@notnl.com>
 */
public class HttpFileServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";

    private static final String HTTP_DATE_GMT_TIMEZONE = "GMT";

    private static final int HTTP_CACHE_SECONDS = 120;

    private static final Pattern RANGE_HEADER = Pattern.compile("bytes=(\\d+)\\-(\\d+)?");

    private static final Pattern INSECURE_URI = Pattern.compile(".*[<>&\"].*");

    private static final MimetypesFileTypeMap MIME_TYPE_MAP = new MimetypesFileTypeMap();

    static {
        MIME_TYPE_MAP.addMimeTypes("text/plain txt text TXT");
        MIME_TYPE_MAP.addMimeTypes("text/html html htmls htm HTML HTM");
        MIME_TYPE_MAP.addMimeTypes("text/css css CSS");
        MIME_TYPE_MAP.addMimeTypes("text/json json JSON");
        MIME_TYPE_MAP.addMimeTypes("image/jpeg jpg jpeg JPG JPEG");
        MIME_TYPE_MAP.addMimeTypes("image/png png PNG");
        MIME_TYPE_MAP.addMimeTypes("image/gif gif GIF");
        MIME_TYPE_MAP.addMimeTypes("image/bmp bmp");
        MIME_TYPE_MAP.addMimeTypes("image/svg+xml svg svgz");
        MIME_TYPE_MAP.addMimeTypes("application/javascript js JS");
        MIME_TYPE_MAP.addMimeTypes("application/pdf pdf");
        MIME_TYPE_MAP.addMimeTypes("application/rtf rtf");
        MIME_TYPE_MAP.addMimeTypes("application/pdf pdf");
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (!ifConnectSuccess(ctx, request)) {
            return;
        }

        String uri = request.uri();
        //System.err.println("+" + uri);
        if (StringUtil.isNullOrEmpty(uri)) {
            sendError(ctx, HttpResponseStatus.BAD_REQUEST);
            return;
        }
        if (uri.contains("?")) {
            uri = uri.substring(0, uri.indexOf('?'));
        }
        if ("/".equals(uri)) {
            sendRedirect(ctx, "/index.html");
            return;
        } else if ("/dl".equals(uri)) {
            sendRedirect(ctx, "/dl/");
            return;
        }

        String path;
        File file;
        if (uri.startsWith("/dl/")) {
            path = Config.DIR + sanitizeUri(uri.substring(3));
            if (path == null) {
                sendError(ctx, HttpResponseStatus.FORBIDDEN);
                return;
            }
            file = new File(path);
            if (file.isHidden() || !file.exists()) {
                sendError(ctx, HttpResponseStatus.NOT_FOUND);
                return;
            }
            if (file.isDirectory()) {
                if (uri.endsWith("/")) {
                    //todo
                    sendListing(ctx, file);
                } else {
                    sendRedirect(ctx, uri + '/');
                }
                return;
            }
        } else {
            path = Config.RESOURCE + sanitizeUri(uri);
            if (path == null) {
                sendError(ctx, HttpResponseStatus.FORBIDDEN);
                return;
            }
            file = new File(path);
            if (file.isHidden() || !file.exists()) {
                sendError(ctx, HttpResponseStatus.NOT_FOUND);
                return;
            }
            if (file.isDirectory()) {
                sendRedirect(ctx, uri + (uri.endsWith("/") ? "" : "/") + "index,html");
                return;
            }
        }

//        if (!file.isFile()) {
//            sendError(ctx, HttpResponseStatus.FORBIDDEN);
//            return;
//        }
        //System.err.println("-" + path);
        // Cache Validation
        if (ifCacheValidate(file.lastModified(),
                request.headers().get(HttpHeaderNames.IF_MODIFIED_SINCE))) {
            sendNotModified(ctx);
            return;
        }
        sendFile(ctx, request, file);
    }

    private boolean ifConnectSuccess(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (!request.decoderResult().isSuccess()) {
            sendError(ctx, HttpResponseStatus.BAD_REQUEST);
            return false;
        }
        if (request.method() != HttpMethod.GET) {
            sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
            return false;
        }
        return true;
    }

    private boolean ifCacheValidate(long fileLastModified, String ifModifiedSince)
            throws ParseException {
        if (ifModifiedSince != null && !ifModifiedSince.isEmpty()) {
            SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
            Date ifModifiedSinceDate = dateFormatter.parse(ifModifiedSince);
            // Only compare up to the second because the datetime format we send to the client
            // does not have milliseconds
            long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
            long fileLastModifiedSeconds = fileLastModified / 1000;
            return ifModifiedSinceDateSeconds == fileLastModifiedSeconds;
        }
        return false;
    }

    private void sendFile(ChannelHandlerContext ctx, FullHttpRequest request, File file) throws IOException {
        RandomAccessFile raf;
        try {
            raf = new RandomAccessFile(file, "r");
        } catch (FileNotFoundException ignore) {
            sendError(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        }
        long fileLength = raf.length();

        long[] range;
        try {
            range = parseRange(request.headers().get(HttpHeaderNames.RANGE), fileLength);
        } catch (IllegalArgumentException iae) {
            sendError(ctx, HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
            return;
        }
        HttpResponse response;
        if (range == null) {
            response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            range = new long[]{0, fileLength - 1};
        } else {
            response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.PARTIAL_CONTENT);
        }
        response.headers().add(HttpHeaderNames.ACCEPT_RANGES, HttpHeaderValues.BYTES);
        response.headers().add(HttpHeaderNames.CONTENT_RANGE, HttpHeaderValues.BYTES
                + " " + range[0] + '-' + range[1] + '/' + fileLength);
        HttpUtil.setContentLength(response, range[1] - range[0] + 1);
        setContentTypeHeader(response, file);
        setDateAndCacheHeaders(response, file);
        if (HttpUtil.isKeepAlive(request)) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }
        // Write the initial line and the header.
        ctx.write(response);
        // Write the content.
        ChannelFuture sendFileFuture;
        ChannelFuture lastContentFuture;
        if (ctx.pipeline().get(SslHandler.class) == null) {
            sendFileFuture = ctx.write(
                    new DefaultFileRegion(raf.getChannel(), range[0], range[1] - range[0] + 1),
                    ctx.newProgressivePromise());
            // Write the end marker.
            lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        } else {
            sendFileFuture
                    = ctx.writeAndFlush(new HttpChunkedInput(new ChunkedFile(raf, range[0], range[1] - range[0] + 1, 8192)),
                            ctx.newProgressivePromise());
            // HttpChunkedInput will write the end marker (LastHttpContent) for us.
            lastContentFuture = sendFileFuture;
        }

        sendFileFuture.addListener(new ChannelProgressiveFutureListener() {
            @Override
            public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) {
//                if (total < 0) { // total unknown
//                    System.err.println(future.channel() + " Transfer progress: " + progress);
//                } else {
//                    System.err.println(future.channel() + " Transfer progress: " + progress + " / " + total);
//                }
            }

            @Override
            public void operationComplete(ChannelProgressiveFuture future) {
                System.err.println(future.channel() + " Transfer complete.");
            }
        });

        // Decide whether to close the connection or not.
        if (!HttpUtil.isKeepAlive(request)) {
            // Close the connection when the whole content is written out.
            lastContentFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * When file timestamp is the same as what the browser is sending up, send a
     * "304 Not Modified"
     *
     * @param ctx Context
     */
    private void sendNotModified(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_MODIFIED);
        setDateHeader(response);
        // Close the connection as soon as the error message is sent.
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private long[] parseRange(String range, long availableLength)
            throws IllegalArgumentException {
        if (StringUtil.isNullOrEmpty(range)) {
            return null;
        }
        Matcher m = RANGE_HEADER.matcher(range);
        if (!m.matches()) {
            throw new IllegalArgumentException("Unsupported range: %s" + range);
        }
        long[] result = new long[2];
        result[0] = Long.parseLong(m.group(1));
        String sed = m.group(2);
        result[1] = StringUtil.isNullOrEmpty(sed) ? availableLength - 1 : Long.parseLong(sed);
        if (result[0] > result[1] || result[1] >= availableLength) {
            throw new IllegalArgumentException("Unsupported range: %s" + range);
        }
        return result;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println(cause.getMessage());
        //cause.printStackTrace();
        if (ctx.channel().isActive()) {
            sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String sanitizeUri(String uri) {
        // Decode the path.
        try {
            uri = URLDecoder.decode(uri, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new Error(e);
        }
        if (uri.isEmpty() || uri.charAt(0) != '/') {
            return null;
        }
        // Convert file separators.
        uri = uri.replace('/', File.separatorChar);
        // Simplistic dumb security check.
        // You will have to do something serious in the production environment.
        if (uri.contains(File.separator + '.')
                || uri.contains('.' + File.separator)
                || uri.charAt(0) == '.' || uri.charAt(uri.length() - 1) == '.'
                || INSECURE_URI.matcher(uri).matches()) {
            return null;
        }
        return uri;
    }

    private void sendListing(ChannelHandlerContext ctx, File dir) throws IOException {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
        ByteBuf buffer = Unpooled.copiedBuffer(Pages.getDirectory(dir), CharsetUtil.UTF_8);
        response.content().writeBytes(buffer);
        buffer.release();

        // Close the connection as soon as the error message is sent.
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void sendRedirect(ChannelHandlerContext ctx, String newUri) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND);
        response.headers().set(HttpHeaderNames.LOCATION, newUri);

        // Close the connection as soon as the error message is sent.
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status, Unpooled.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

        // Close the connection as soon as the error message is sent.
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Sets the Date header for the HTTP response
     *
     * @param response HTTP response
     */
    private void setDateHeader(FullHttpResponse response) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        Calendar time = new GregorianCalendar();
        response.headers().set(HttpHeaderNames.DATE, dateFormatter.format(time.getTime()));
    }

    /**
     * Sets the Date and Cache headers for the HTTP Response
     *
     * @param response HTTP response
     * @param fileToCache file to extract content type
     */
    private void setDateAndCacheHeaders(HttpResponse response, File fileToCache) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        // Date header
        Calendar time = new GregorianCalendar();
        response.headers().set(HttpHeaderNames.DATE, dateFormatter.format(time.getTime()));

        // Add cache headers
        time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
        response.headers().set(HttpHeaderNames.EXPIRES, dateFormatter.format(time.getTime()));
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
        response.headers().set(
                HttpHeaderNames.LAST_MODIFIED, dateFormatter.format(new Date(fileToCache.lastModified())));
    }

    /**
     * Sets the content type header for the HTTP Response
     *
     * @param response HTTP response
     * @param file file to extract content type
     */
    private void setContentTypeHeader(HttpResponse response, File file) {
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, MIME_TYPE_MAP.getContentType(file.getName()));
    }

}
