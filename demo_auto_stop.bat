@echo off
echo ==========================================
echo   KAFKA CONSUMER AUTO-STOP DEMONSTRATION
echo ==========================================
echo.

set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_202
cd /d "D:\8 Semester\Big Data\Kafka_Order_Stream_Project_3912"

echo 1. Current message backlog status:
docker exec -it kafka kafka-consumer-groups --bootstrap-server localhost:29092 --describe --group order-consumer-demo-1763488950676 2>nul

echo.
echo 2. Starting OrderConsumer with Auto-Stop feature...
echo    - Will process ALL available messages
echo    - Will stop automatically after 3 empty polls
echo    - Will show final processing summary
echo.
echo Press Ctrl+C if consumer runs too long (should auto-stop)
echo.

mvn exec:java -Dexec.mainClass=com.kafka.assignment.OrderConsumer

echo.
echo ==========================================
echo   DEMONSTRATION COMPLETED
echo ==========================================
echo The consumer should have:
echo - Processed all available orders
echo - Shown "No new messages" counter
echo - Stopped automatically after 3 empty polls
echo - Displayed final processing summary
echo ==========================================

pause
