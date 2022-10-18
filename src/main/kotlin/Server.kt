import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.text.SimpleDateFormat


class Server(val publidDir: File, port: Int) : NanoHTTPD("0.0.0.0", port)
{
    private val fmt = SimpleDateFormat("YYYY-MM-dd HH:mm:ss")

    /**
     * 服务主函数
     */
    override fun serve(session: IHTTPSession): Response
    {
        val timestamp = fmt.format(System.currentTimeMillis())
        val start = System.currentTimeMillis()
        val res: Response = handleRequest(session)
        val elapsed = System.currentTimeMillis() - start
        val statusCode = res.status.requestStatus
        val uri = session.uri
        val ip: String = session.javaClass.getDeclaredField("remoteIp").also { it.isAccessible = true }.get(session) as String

        println(String.format("[ %s ] %3s | %-15s | %s (%dms)", timestamp, statusCode, ip, uri, elapsed))

        return res
    }

    /**
     * 服务具体处理过程
     */
    fun handleRequest(session: IHTTPSession): Response
    {
        try {
            // Remove URL arguments
            val uri = session.uri.trim().replace(File.separatorChar, '/')
            val path = if ('?' in uri) uri.substring(0, uri.indexOf('?')) else uri

            // Prohibit getting out of current directory
            if ("../" in path)
                return ResponseHelper.buildForbiddenResponse("Won't serve ../ for security reasons.")

            // if request on a directory
            if (path.endsWith("/"))
                return ResponseHelper.buildForbiddenResponse("Directory is unable to show")

            // 下载文件
            val file = File(publidDir, path.substring(1))

            if(!file.exists())
                return ResponseHelper.buildNotFoundResponse(path)

            if(file.isFile)
                return ResponseHelper.buildFileResponse(file)

            // 100%不会执行到这里
            return ResponseHelper.buildPlainTextResponse(path)

        } catch (e: Exception) {
            return ResponseHelper.buildInternalErrorResponse(e.stackTraceToString())
        }
    }
}
