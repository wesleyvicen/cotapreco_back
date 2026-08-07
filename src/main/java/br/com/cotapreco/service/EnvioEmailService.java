package br.com.cotapreco.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.MessagingException;

@Service @RequiredArgsConstructor @Slf4j
public class EnvioEmailService {
    private final JavaMailSender enviador;
    @Value("${app.email.remetente}") private String remetente;

    public void enviarRedefinicaoSenhaRepresentante(String destinatario, String nome, String link) {
        enviarRedefinicaoSenha(destinatario, nome, link, "do representante");
    }

    public void enviarRedefinicaoSenhaUsuario(String destinatario, String nome, String link) {
        enviarRedefinicaoSenha(destinatario, nome, link, "da sua conta");
    }

    private void enviarRedefinicaoSenha(String destinatario, String nome, String link, String tipoConta) {
        String texto = "Olá, " + nome + ".\n\nRecebemos uma solicitação para redefinir a senha " + tipoConta + " no CotaPreço. "
            + "Use o link abaixo para criar uma nova senha. Ele é válido por 30 minutos e só pode ser utilizado uma vez.\n\n" + link
            + "\n\nSe você não solicitou a redefinição, ignore este e-mail. Nenhuma alteração será feita na sua conta.";
        try {
            var mensagem = enviador.createMimeMessage();
            var ajuda = new MimeMessageHelper(mensagem, true, "UTF-8");
            ajuda.setFrom(remetente);
            ajuda.setTo(destinatario);
            ajuda.setSubject("Redefinição de senha — CotaPreço");
            ajuda.setText(texto, templateHtml(nome, link, tipoConta));
            enviador.send(mensagem);
        } catch (MailException | MessagingException ex) {
            // A resposta do endpoint é sempre genérica para não revelar contas cadastradas.
            log.warn("Não foi possível enviar o e-mail de redefinição de senha: {}", ex.getMessage());
        }
    }

    private String templateHtml(String nome, String link, String tipoConta) {
        String nomeSeguro = escaparHtml(nome);
        String linkSeguro = escaparHtml(link);
        return """
            <!doctype html>
            <html lang="pt-BR"><body style="margin:0;padding:0;background:#f4f8f6;font-family:Arial,sans-serif;color:#18332b;">
              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="padding:32px 16px;background:#f4f8f6;"><tr><td align="center">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="max-width:600px;background:#ffffff;border-radius:16px;overflow:hidden;">
                  <tr><td style="padding:26px 32px;background:#0d3e31;color:#ffffff;">
                    <div style="font-size:22px;font-weight:700;letter-spacing:-.4px;">Cota<span style="color:#f3c969;">Preço</span></div>
                    <div style="margin-top:5px;font-size:12px;color:#c7d9d2;">Compras inteligentes</div>
                  </td></tr>
                  <tr><td style="padding:34px 32px 28px;">
                    <div style="display:inline-block;padding:7px 10px;border-radius:999px;background:#e7f4ef;color:#19835f;font-size:11px;font-weight:700;letter-spacing:.7px;text-transform:uppercase;">Segurança da conta</div>
                    <h1 style="margin:20px 0 12px;font-size:26px;line-height:1.25;color:#18332b;">Redefina sua senha</h1>
                    <p style="margin:0 0 18px;font-size:15px;line-height:1.6;color:#526a62;">Olá, %s.</p>
                    <p style="margin:0 0 26px;font-size:15px;line-height:1.6;color:#526a62;">Recebemos uma solicitação para redefinir a senha %s no CotaPreço. Clique no botão abaixo para criar uma nova senha.</p>
                    <table role="presentation" cellspacing="0" cellpadding="0" border="0"><tr><td style="border-radius:10px;background:#12634a;"><a href="%s" style="display:inline-block;padding:14px 22px;color:#ffffff;font-size:15px;font-weight:700;text-decoration:none;">Redefinir minha senha</a></td></tr></table>
                    <div style="margin:28px 0 0;padding:14px 16px;border-radius:10px;background:#f5f8f7;color:#526a62;font-size:13px;line-height:1.55;">
                      <strong style="color:#18332b;">Link protegido</strong><br>Válido por 30 minutos e para um único uso.
                    </div>
                    <p style="margin:24px 0 0;font-size:12px;line-height:1.55;color:#71857d;">Se o botão não funcionar, copie e cole este endereço no navegador:<br><a href="%s" style="color:#12634a;word-break:break-all;">%s</a></p>
                  </td></tr>
                  <tr><td style="padding:20px 32px;background:#f7faf8;border-top:1px solid #e1ebe6;color:#71857d;font-size:12px;line-height:1.55;">Se você não solicitou esta alteração, pode ignorar este e-mail. Nenhuma mudança será feita na sua conta.</td></tr>
                </table>
              </td></tr></table>
            </body></html>
            """.formatted(nomeSeguro, tipoConta, linkSeguro, linkSeguro, linkSeguro);
    }

    private String escaparHtml(String valor) {
        return valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
