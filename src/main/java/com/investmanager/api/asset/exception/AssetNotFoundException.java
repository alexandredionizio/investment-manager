package com.investmanager.api.asset.exception;

public class AssetNotFoundException extends RuntimeException {

    public AssetNotFoundException(Long id){
        super("Ativo não encontrado com o id: " + id);
    }
}
