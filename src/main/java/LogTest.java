import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class LogTest implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LogTest.class);

    @Override
    public void run(String... args) {
        log.info("Приложение запущено, логирование работает");
        log.debug("Это debug-сообщение");
        log.warn("Это предупреждение");
        log.error("Это ошибка (тестовая, не пугайся)");
    }
}
