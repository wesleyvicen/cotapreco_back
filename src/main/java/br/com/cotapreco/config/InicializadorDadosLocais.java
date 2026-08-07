package br.com.cotapreco.config;

import br.com.cotapreco.enums.PerfilUsuario;
import br.com.cotapreco.model.*;
import br.com.cotapreco.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration @RequiredArgsConstructor
public class InicializadorDadosLocais {
    @Bean CommandLineRunner seedLocalData(EmpresaRepository companies, UsuarioRepository users, PasswordEncoder encoder,
        @Value("${app.seed-local-data:false}") boolean seedLocalData) {
        return args -> { if (seedLocalData && users.count() == 0) { Empresa company = new Empresa(); company.setNome("Farmácia Exemplo"); company.setSlug("farmacia-exemplo"); companies.save(company); Usuario user = new Usuario(); user.setEmpresa(company); user.setNome("Administrador"); user.setEmail("admin@cotapreco.local"); user.setSenhaHash(encoder.encode("Cotapreco@123")); user.setPerfil(PerfilUsuario.ADMIN); users.save(user); } };
    }
}
