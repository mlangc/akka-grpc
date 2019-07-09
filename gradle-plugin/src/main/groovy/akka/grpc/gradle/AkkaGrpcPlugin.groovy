package akka.grpc.gradle

import org.apache.commons.lang.SystemUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies

class AkkaGrpcPlugin implements Plugin<Project>, DependencyResolutionListener {

    final String pluginVersion = AkkaGrpcPlugin.class.package.implementationVersion

    final String protocVersion = "3.8.0"
    final String grpcVersion = "1.22.0" // checked synced by GrpcVersionSyncCheckPlugin

    Project project

    private void skipTillFirstAtAndCopy(File src, File dst) {
        FileInputStream fin = new FileInputStream(src)
        try {
            FileOutputStream fout = new FileOutputStream(dst, false)
            try {
                BufferedInputStream bin = new BufferedInputStream(fin)
                BufferedOutputStream bout = new BufferedOutputStream(fout)

                int lastRead = -1
                boolean stop = false;
                while (!stop) {
                    lastRead = bin.read()
                    stop = (lastRead == -1 || lastRead == '@')
                }

                if (lastRead != -1) {
                    byte[] buffer = new byte[64]
                    bout.write(lastRead)
                    int bytesRead = -1;
                    while ((bytesRead = bin.read(buffer)) != -1) {
                        bout.write(buffer, 0, bytesRead)
                    }
                    
                    bout.flush()
                }
            } finally {
                fout.close()
            }
        } finally {
            fin.close()
        }
    }

    @Override
    void apply(Project project) {
        this.project = project
        project.gradle.addListener(this)

        def extension = project.extensions.create('akkaGrpc', AkkaGrpcPluginExtension, project)
        String assemblySuffix = SystemUtils.IS_OS_WINDOWS ? "bat" : "jar"

        if (SystemUtils.IS_OS_WINDOWS) {
            Configuration assembliesConfig = project.getConfigurations().create("codegen-assemblies");
            assembliesConfig.setTransitive(false)
            project.getDependencies().add(assembliesConfig.getName(), "com.lightbend.akka.grpc:akka-grpc-codegen_2.12:9.9.9-SNAPSHOT:assembly")
            project.getDependencies().add(assembliesConfig.getName(), "com.lightbend.akka.grpc:akka-grpc-scalapb-protoc-plugin_2.12:9.9.9-SNAPSHOT:assembly")
            for (File assembly : assembliesConfig.getFiles()) {
                File batFile = new File(assembly.getParentFile(), assembly.getName().replace(".jar", ".bat"))
                skipTillFirstAtAndCopy(assembly, batFile)
            }
        }

        project.configure(project) {
            boolean isScala = "${extension.language}".toLowerCase() == "scala"
            apply plugin: 'com.google.protobuf'

            protobuf {
                protoc {
                    // Get protobuf from maven central instead of
                    // using the installed version:
                    artifact = "com.google.protobuf:protoc:${protocVersion}"
                }

                plugins {
                    akkaGrpc {
                        artifact = "com.lightbend.akka.grpc:akka-grpc-codegen_2.12:9.9.9-SNAPSHOT:assembly@$assemblySuffix"
                    }
                    if (isScala) {
                        scalapb {
                            artifact = "com.lightbend.akka.grpc:akka-grpc-scalapb-protoc-plugin_2.12:9.9.9-SNAPSHOT:assembly@$assemblySuffix"
                        }
                    }
                }
                sourceSets {
                    main {
                        proto {
                            srcDir 'src/main/protobuf'
                            srcDir 'src/main/proto'
                            // Play conventions:
                            srcDir 'app/protobuf'
                            srcDir 'app/proto'
                        }
                    }
                }

                if (isScala) {
                    sourceSets {
                        main {
                            scala {
                                srcDir 'build/generated/source/proto/main/akkaGrpc'
                                srcDir 'build/generated/source/proto/main/scalapb'
                            }
                        }
                    }
                }

                generateProtoTasks {
                    all().each { task ->
                        if (isScala) {
                            task.builtins {
                                remove java
                            }
                        }

                        task.plugins {
                            akkaGrpc {
                                option "language=${extension.language}"
                                option "generate_client=${extension.generateClient}"
                                option "generate_server=${extension.generateServer}"
                                option "server_power_apis=${extension.serverPowerApis}"
                                option "use_play_actions=${extension.usePlayActions}"
                                option "extra_generators=${extension.extraGenerators.join(';')}"
                                //option "logfile=${logFile.getAbsolutePath()}"
                                if (extension.generatePlay) {
                                    option "generate_play=true"
                                }
                                if (isScala) {
                                    option "flat_package"
                                }
                            }
                            if (isScala) {
                                scalapb {
                                    option "flat_package"
                                }
                            }
                        }
                    }
                }
            }

            /*println project.getTasks()
            project.task("printProtocLogs") {
                doLast {
                    Files.lines(logFile.toPath()).forEach { line ->
                        if (line.startsWith("[info]")) logger.info(line.substring(7))
                        else if (line.startsWith("[debug]")) logger.debug(line.substring(7))
                        else if (line.startsWith("[warn]")) logger.warn(line.substring(6))
                        else if (line.startsWith("[error]")) logger.error(line.substring(7))
                    }
                }
            }
            project.getTasks().getByName("compileJava").dependsOn("printProtocLogs")*/
        }
    }


    @Override
    void beforeResolve(ResolvableDependencies resolvableDependencies) {
        def compileDeps = project.getConfigurations().getByName("compile").getDependencies()
        compileDeps.add(project.getDependencies().create("com.lightbend.akka.grpc:akka-grpc-runtime_2.12:9.9.9-SNAPSHOT"))
        // TODO #115 grpc-stub is only needed for the client. Can we use the 'suggestedDependencies' somehow?
        compileDeps.add(project.getDependencies().create("io.grpc:grpc-stub:${grpcVersion}"))
        project.gradle.removeListener(this)
    }

    @Override
    void afterResolve(ResolvableDependencies resolvableDependencies) {

    }
}

class AkkaGrpcPluginExtension {

    String language
    boolean generateClient = true
    boolean generateServer = true
    boolean generatePlay = false
    boolean serverPowerApis = false
    boolean usePlayActions = false
    List<String> extraGenerators = []

    AkkaGrpcPluginExtension(Project project) {
        if (project.plugins.hasPlugin("scala"))
            language = "Scala"
        else
            language = "Java"

    }
}
