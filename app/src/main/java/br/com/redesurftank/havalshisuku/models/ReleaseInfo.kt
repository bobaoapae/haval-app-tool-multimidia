package br.com.redesurftank.havalshisuku.models

data class ReleaseInfo(
    val tag: String,
    val downloadUrl: String,
    val isPrerelease: Boolean,
    /**
     * O texto da release, como escrito na publicacao. Vem de graca na MESMA consulta que ja
     * buscava a versao — nao precisa de arquivo separado nem de outra requisicao.
     *
     * Vazio quando a release foi publicada sem descricao; a tela trata esse caso.
     */
    val notes: String = ""
)

data class UpdateCheckResult(
    val latestRelease: ReleaseInfo?,
    val latestPreview: ReleaseInfo?
)
