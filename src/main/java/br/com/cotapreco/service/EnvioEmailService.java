package br.com.cotapreco.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor @Slf4j
public class EnvioEmailService {
    private final JavaMailSender enviador;
    @Value("${app.email.remetente}") private String remetente;

    public void enviarRedefinicaoSenhaRepresentante(String destinatario, String nome, String link) {
        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setFrom(remetente);
        mensagem.setTo(destinatario);
        mensagem.setSubject("Redefinição de senha — CotaPreço");
        mensagem.setText("Olá, " + nome + ".\n\nUse o link abaixo para criar uma nova senha. "
            + "Ele é válido por 30 minutos e só pode ser utilizado uma vez.\n\n" + link
            + "\n\nSe você não solicitou a redefinição, ignore este e-mail.");
        try { enviador.send(mensagem); } catch (MailException ex) {
            // A resposta do endpoint é sempre genérica para não revelar contas cadastradas.
            log.warn("Não foi possível enviar o e-mail de redefinição de senha do representante: {}", ex.getMessage());
        }
    }
}
