import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.FileWriter;
import java.time.Duration;
import java.util.List;

public class MercadoLivreScraperTest {

    private boolean paginaBloqueadaOuVerificacao(WebDriver driver) {
        String url = driver.getCurrentUrl() == null ? "" : driver.getCurrentUrl().toLowerCase();
        String bodyText = driver.findElement(By.tagName("body")).getText().toLowerCase();

        return url.contains("account-verification")
                || url.contains("suspicious-traffic")
                || url.contains("captcha")
                || bodyText.contains("para continuar, acesse")
                || bodyText.contains("verificação de conta")
                || bodyText.contains("suspicious traffic")
                || bodyText.contains("hubo un error accediendo a esta pagina")
                || bodyText.contains("acessar a página principal");
    }

    private WebElement esperarCampoPesquisa(WebDriver driver, WebDriverWait wait) {
        List<String> seletores = List.of(
                "input[name='as_word']",
                "input.nav-search-input",
                "input[type='search']",
                "input[placeholder*='Buscar']"
        );

        for (String seletor : seletores) {
            try {
                return wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(seletor)));
            } catch (Exception ignored) {
                // tenta o próximo seletor
            }
        }

        throw new RuntimeException("Campo de busca não encontrado no layout atual do Mercado Livre.");
    }

    @Test
    public void buscarProdutoEExportarCSV() throws Exception {

        ChromeOptions options = new ChromeOptions();

        options.setBinary("/usr/bin/google-chrome");
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");

        WebDriver driver = new ChromeDriver(options);

        try {

            WebDriverWait wait = new WebDriverWait(
                    driver,
                    Duration.ofSeconds(20)
            );

            // 1. Acessar o Mercado Livre
            driver.get("https://www.mercadolivre.com.br/");

            System.out.println(
                    "Página inicial: " + driver.getTitle()
            );

            // 2. Verificar se a página de busca foi bloqueada ou redirecionada
            if (paginaBloqueadaOuVerificacao(driver)) {
                System.out.println("O Mercado Livre solicitou verificação de conta ou bloqueou o acesso do ambiente.");
                System.out.println("O ambiente não recebeu a página de resultados esperada.");
                return;
            }

            // 3. Localizar campo de pesquisa com fallback para layouts atuais
            var campoPesquisa = esperarCampoPesquisa(driver, wait);

            // 4. Pesquisar Xiaomi POCO
            campoPesquisa.sendKeys("Xiaomi POCO");
            campoPesquisa.submit();

            Thread.sleep(3000);

            System.out.println(
                    "URL após pesquisa: "
                    + driver.getCurrentUrl()
            );

            // 5. Verificar se o Mercado Livre pediu login após a busca
            String textoPagina = driver
                    .findElement(By.tagName("body"))
                    .getText();

            if (paginaBloqueadaOuVerificacao(driver)) {
                System.out.println(
                        "O Mercado Livre solicitou verificação de conta."
                );

                System.out.println(
                        "O ambiente do Codespace não recebeu " +
                        "a página de resultados."
                );

                return;
            }

            // 6. Localizar produtos
            var produtos = driver.findElements(
                    By.cssSelector("div.poly-card")
            );

            if (produtos.isEmpty()) {

                produtos = driver.findElements(
                        By.cssSelector("li.ui-search-layout__item")
                );
            }

            if (produtos.isEmpty()) {

                throw new RuntimeException(
                        "Nenhum produto encontrado."
                );
            }

            // 7. Primeiro produto
            var primeiroProduto = produtos.get(0);

            // 8. Descrição
            String descricao;

            var titulos = primeiroProduto.findElements(
                    By.cssSelector("a.poly-component__title")
            );

            if (!titulos.isEmpty()) {

                descricao = titulos.get(0).getText();

            } else {

                descricao = primeiroProduto.getText();
            }

            // 9. Preço
            var precos = primeiroProduto.findElements(
                    By.cssSelector(".andes-money-amount__fraction")
            );

            String preco = precos.isEmpty()
                    ? "Preço não encontrado"
                    : precos.get(0).getText();

            // 10. URL
            var links = primeiroProduto.findElements(
                    By.cssSelector("a")
            );

            String url = links.isEmpty()
                    ? "URL não encontrada"
                    : links.get(0).getAttribute("href");

            // 11. Criar CSV
            try (FileWriter writer =
                         new FileWriter("produto.csv")) {

                writer.write("Descrição,Preço,URL\n");

                writer.write(
                        "\"" +
                        descricao.replace("\"", "\"\"") +
                        "\"," +
                        "\"" +
                        preco.replace("\"", "\"\"") +
                        "\"," +
                        "\"" +
                        url.replace("\"", "\"\"") +
                        "\"\n"
                );
            }

            System.out.println("--------------------------------");
            System.out.println("PRODUTO ENCONTRADO");
            System.out.println("Descrição: " + descricao);
            System.out.println("Preço: " + preco);
            System.out.println("URL: " + url);
            System.out.println("--------------------------------");
            System.out.println("produto.csv criado com sucesso!");

        } finally {

            driver.quit();
        }
    }
}