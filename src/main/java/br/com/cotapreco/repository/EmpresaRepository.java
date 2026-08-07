package br.com.cotapreco.repository;
import br.com.cotapreco.model.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EmpresaRepository extends JpaRepository<Empresa, Long> {
    boolean existsByCnpj(String cnpj);
}
