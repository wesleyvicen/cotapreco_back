package br.com.cotapreco.service;
import br.com.cotapreco.dto.EmpresaDtos.*;
import br.com.cotapreco.model.Empresa;
import br.com.cotapreco.repository.EmpresaRepository;
import br.com.cotapreco.exception.RegraNegocioException;
import br.com.cotapreco.security.UsuarioAtualService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

@Service @RequiredArgsConstructor
public class EmpresaService {
    private final UsuarioAtualService usuarioAtual;
    private final EmpresaRepository repositorio;
    @Transactional(readOnly=true) public VisaoEmpresa obter(){return visualizar(usuarioAtual.get().getEmpresa());}
    @Transactional public VisaoEmpresa atualizar(SolicitacaoEmpresa solicitacao){Empresa empresa=usuarioAtual.get().getEmpresa();empresa.setNome(solicitacao.nome().trim());empresa.setCnpj(solicitacao.cnpj());try{return visualizar(repositorio.saveAndFlush(empresa));}catch(DataIntegrityViolationException ex){throw new RegraNegocioException("Este CNPJ já está cadastrado em outra farmácia.");}}
    private VisaoEmpresa visualizar(Empresa empresa){return new VisaoEmpresa(empresa.getId(),empresa.getNome(),empresa.getCnpj());}
}
