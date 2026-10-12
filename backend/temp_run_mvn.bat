@echo off
set "PATH=C:\Windows\System32;C:\Program Files\Eclipse Adoptium\jdk-21.0.2.13-hotspot\bin"
"%~dp0\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd" -v
pause
