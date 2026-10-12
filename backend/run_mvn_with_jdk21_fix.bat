@echo off
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.2.13-hotspot"
set "PATH=%JAVA_HOME%\bin;C:\Windows\System32;C:\Windows\System32\WindowsPowerShell\v1.0"
call "%~dp0mvnw.cmd" -v
