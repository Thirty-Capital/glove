package br.com.correios.webservice.rastro;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.ws.Action;
import javax.xml.ws.RequestWrapper;
import javax.xml.ws.ResponseWrapper;
import java.util.List;

/**
 * This class was generated by the JAX-WS RI. JAX-WS RI 2.2.9-b130926.1035
 * Generated source version: 2.2
 *
 */
@WebService(name = "Service", targetNamespace = "http://resource.webservice.correios.com.br/")
@XmlSeeAlso({
    ObjectFactory.class
})
public interface Service {

    /**
     *
     * @param senha
     * @param tipo
     * @param resultado
     * @param lingua
     * @param usuario
     * @param objetos
     * @return returns br.com.correios.webservice.rastro.Sroxml
     */
    @WebMethod(action = "buscaEventos")
    @WebResult(targetNamespace = "")
    @RequestWrapper(localName = "buscaEventos", targetNamespace = "http://resource.webservice.correios.com.br/", className = "br.com.correios.webservice.rastro.BuscaEventos")
    @ResponseWrapper(localName = "buscaEventosResponse", targetNamespace = "http://resource.webservice.correios.com.br/", className = "br.com.correios.webservice.rastro.BuscaEventosResponse")
    @Action(input = "buscaEventos", output = "http://resource.webservice.correios.com.br/Service/buscaEventosResponse")
    Sroxml buscaEventos(
            @WebParam(name = "usuario", targetNamespace = "") String usuario,
            @WebParam(name = "senha", targetNamespace = "") String senha,
            @WebParam(name = "tipo", targetNamespace = "") String tipo,
            @WebParam(name = "resultado", targetNamespace = "") String resultado,
            @WebParam(name = "lingua", targetNamespace = "") String lingua,
            @WebParam(name = "objetos", targetNamespace = "") String objetos);

    /**
     *
     * @param senha
     * @param tipo
     * @param resultado
     * @param lingua
     * @param usuario
     * @param objetos
     * @return returns br.com.correios.webservice.rastro.Sroxml
     */
    @WebMethod(action = "buscaEventosLista")
    @WebResult(targetNamespace = "")
    @RequestWrapper(localName = "buscaEventosLista", targetNamespace = "http://resource.webservice.correios.com.br/", className = "br.com.correios.webservice.rastro.BuscaEventosLista")
    @ResponseWrapper(localName = "buscaEventosListaResponse", targetNamespace = "http://resource.webservice.correios.com.br/", className = "br.com.correios.webservice.rastro.BuscaEventosListaResponse")
    @Action(input = "buscaEventosLista", output = "http://resource.webservice.correios.com.br/Service/buscaEventosListaResponse")
    Sroxml buscaEventosLista(
            @WebParam(name = "usuario", targetNamespace = "") String usuario,
            @WebParam(name = "senha", targetNamespace = "") String senha,
            @WebParam(name = "tipo", targetNamespace = "") String tipo,
            @WebParam(name = "resultado", targetNamespace = "") String resultado,
            @WebParam(name = "lingua", targetNamespace = "") String lingua,
            @WebParam(name = "objetos", targetNamespace = "") List<String> objetos);
}