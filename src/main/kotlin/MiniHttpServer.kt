import fi.iki.elonen.NanoHTTPD
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.net.BindException
import java.net.URLDecoder
import java.security.KeyStore
import java.util.*
import java.util.jar.JarFile
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLServerSocketFactory
import kotlin.system.exitProcess

class MiniHttpServer
{
    fun main()
    {
        val workdir = File(System.getProperty("user.dir"))
        val configFile = File(workdir, "config.yml")

        if(!workdir.exists())
            exitWithError("找不到工作目录: ${workdir.path}")

        if(!configFile.exists())
        {
            if (!isPackaged)
                exitWithError("找不到配置文件: ${configFile.path}")

            // 解压默认文件
            configFile.writeText(JarFile(jarFile).use { jar ->
                val configFileInZip = jar.getJarEntry("config.yml") ?: throw FileNotFoundException("config.yml 无法解压")
                jar.getInputStream(configFileInZip).use { it.readBytes().decodeToString() }
            })
        }

        val config: AppConfig
        val server: Server

        try {
            val configYaml = Yaml().load(configFile.readText()) as HashMap<String, Any>

            config = AppConfig(
                port = configYaml["port"]?.run { this as Int } ?: 8850,
                certificateFile = configYaml["jks-certificate-file"]?.run { this as String } ?: "",
                certificatePass = configYaml["jks-certificate-pass"]?.run { this as String } ?: "",
            )
        } catch (e: YAMLException) {
            exitWithError("配置文件读取出错(格式不正确)，可能的位置和原因: ${e.cause?.message}")
        }

        val publidDir = File(workdir, "public")

        if (!(File(publidDir, "current-version.txt")).exists())
            exitWithError("找不到public/current-version.txt，启动失败，请检查此文件是否存在")

        try {
            server = Server(publidDir, config.port)

            if (config.certificateFile.isNotEmpty() && config.certificatePass.isNotEmpty())
            {
                server.makeSecure(loadCertificate(config.certificateFile, config.certificatePass), null)
                println("SSL证书已加载")
            }

            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)

            println("Listening on: ${config.port}")
            println("API地址: http://localhost:${config.port}/current-version.txt")

            println()
            println("使用提示1：显示的所有报错信息都不用管，直接忽略就好！")
            println("使用提示2：可以使用之类stop或者s来退出程序")
            println("恭喜，程序启动成功！")
        } catch (e: BindException) {
            println("端口监听失败，可能是端口冲突，原因: ${e.message}")
            exitProcess(1)
        }

        // 读取控制台输入
        val scanner = Scanner(System.`in`)
        while (true)
        {
            val line = scanner.nextLine()
            if (line == "stop" || line == "s")
                exitProcess(1)
        }
    }

    fun loadCertificate(certificateFile: String, certificatePass: String): SSLServerSocketFactory
    {
        if (File(certificateFile).exists())
        {
            val keystore = KeyStore.getInstance(KeyStore.getDefaultType())
            val keystoreStream = FileInputStream(certificateFile)

            try {
                keystore.load(keystoreStream, certificatePass.toCharArray())
            } catch (e: IOException) {
                println("SSL证书密码不正确")
                exitProcess(1)
            }

            val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            keyManagerFactory.init(keystore, certificatePass.toCharArray())
            return NanoHTTPD.makeSSLSocketFactory(keystore, keyManagerFactory)
        } else {
            println("SSL证书文件找不到: $certificateFile")
            exitProcess(1)
        }
    }

    fun exitWithError(message: String): Nothing
    {
        println(message)
        exitProcess(1)
    }

    /**
     * 程序是否被打包
     */
    val isPackaged: Boolean get() = MiniHttpServer.javaClass.getResource("")?.protocol != "file"

    /**
     * 获取当前Jar文件路径（仅打包后有效）
     */
    val jarFile: File
        get() {
            val url = URLDecoder.decode(MiniHttpServer.javaClass.protectionDomain.codeSource.location.file, "UTF-8").replace("\\", "/")
            return File(if (url.endsWith(".class") && "!" in url) {
                val path = url.substring(0, url.lastIndexOf("!"))
                if ("file:/" in path) path.substring(path.indexOf("file:/") + "file:/".length) else path
            } else url)
        }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MiniHttpServer().main()
        }
    }
}