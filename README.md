# Keycloak Provider avec HashiCorp Vault

Provider Keycloak personnalisé pour IPRES/CSS utilisant HashiCorp Vault pour la gestion sécurisée des credentials de base de données.

## 🎯 Fonctionnalités

- **Authentification externe** : Utilise la table `jhi_user` existante
- **Sécurité renforcée** : Credentials stockés dans HashiCorp Vault
- **Cache intelligent** : Mise en cache des credentials avec TTL de 5 minutes
- **Validation BCrypt** : Support des mots de passe hashés avec BCrypt
- **Gestion des rôles** : Mapping automatique des authorities depuis `jhi_authority`

## 🔧 Configuration

### 1. HashiCorp Vault

#### Démarrage en mode développement
```bash
vault server -dev -dev-root-token-id=hvs.hWP5WownECWEzuDigz3QRGfZ
```

#### Configuration des secrets
```bash
export VAULT_ADDR='http://localhost:8200'
export VAULT_TOKEN='hvs.hWP5WownECWEzuDigz3QRGfZ'

vault kv put secret/ndamli_db_access_dev \
  ndamli_db_backend_url='jdbc:mysql://localhost:3306/cssipres_preprod?allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8&useSSL=false&useLegacyDatetimeCode=false&serverTimezone=UTC&createDatabaseIfNotExist=true' \
  ndamli_db_backend_username='suntel' \
  ndamli_db_backend_password='suntel'
```

#### Vérification
```bash
vault kv get secret/ndamli_db_access_dev
```

Ou via curl :
```bash
curl -H 'X-Vault-Token: hvs.hWP5WownECWEzuDigz3QRGfZ' \
     http://localhost:8200/v1/secret/data/ndamli_db_access_dev
```

### 2. Keycloak

1. Allez dans **Admin Console → Votre Realm → User Federation**
2. Ajoutez le provider **"ndamli-provider"**
3. Configurez les paramètres :
   - **Vault URL** : `http://localhost:8200`
   - **Vault Token** : `hvs.hWP5WownECWEzuDigz3QRGfZ`
   - **Vault Secret Path** : `secret/data/ndamli_db_access_dev`
   - **Database Driver** : `com.mysql.cj.jdbc.Driver`
4. Cliquez sur **"Test connection"** → devrait être vert ✅
5. Sauvegardez

## 🏗️ Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│    Keycloak     │    │  HashiCorp      │    │    MySQL        │
│                 │    │     Vault       │    │   Database      │
│  ┌───────────┐  │    │                 │    │                 │
│  │ Provider  │──┼────┤  Credentials    │    │   jhi_user      │
│  │           │  │    │   Storage       │    │   jhi_authority │
│  └───────────┘  │    │                 │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## 🔐 Sécurité

### Avantages de Vault
- ✅ **Pas de credentials en dur** dans la configuration Keycloak
- ✅ **Chiffrement** des secrets au repos et en transit
- ✅ **Audit complet** des accès aux credentials
- ✅ **Rotation automatique** possible des mots de passe
- ✅ **Policies granulaires** d'accès aux secrets
- ✅ **Cache intelligent** avec TTL pour les performances

### Validation des mots de passe
- Support des hash **BCrypt** (format `$2a$`, `$2b$`, `$2x$`, `$2y$`)
- Validation du coût BCrypt (entre 4 et 31)
- Compatibilité avec Spring Security BCrypt

## 🧪 Tests

### Exécution des tests
```bash
mvn test
```

### Tests disponibles
- **DatabaseConnectionTest** : Tests de connexion JPA/Hibernate
- **VaultClientTest** : Tests du client Vault
- **VaultIntegrationTest** : Tests d'intégration Vault + DB
- **AuthenticationTest** : Tests d'authentification BCrypt
- **ProviderIntegrationTest** : Tests du provider complet

## 📊 Monitoring

### Logs Keycloak
Recherchez les messages avec emojis dans les logs :
```
🔐 Début de validation des credentials pour l'utilisateur: admin
✅ Utilisateur trouvé: admin (ID: 1)
✅ Authentification RÉUSSIE pour l'utilisateur: admin
```

### Métriques Vault
- Temps de réponse des appels Vault
- Taux de cache hit/miss
- Erreurs d'authentification Vault

## 🚀 Déploiement Production

### 1. Configuration Vault Production
```bash
# Exemple de configuration Vault production
vault auth enable ldap
vault write auth/ldap/config \
    url="ldap://ldap.company.com" \
    userdn="ou=Users,dc=company,dc=com" \
    groupdn="ou=Groups,dc=company,dc=com"
```

### 2. Policies de sécurité
```hcl
# Policy pour le provider Keycloak
path "secret/data/ndamli_db_access_*" {
  capabilities = ["read"]
}
```

### 3. Rotation des credentials
```bash
# Script de rotation automatique
vault write database/config/mysql \
    plugin_name=mysql-database-plugin \
    connection_url="{{username}}:{{password}}@tcp(localhost:3306)/" \
    allowed_roles="keycloak-role" \
    username="vault" \
    password="vault-password"
```

## 🔧 Dépannage

### Problèmes courants

1. **"No credentials" dans Keycloak** → ✅ **NORMAL !**
   - Les mots de passe restent dans votre base MySQL
   - L'authentification se fait via le provider

2. **Erreur de connexion Vault**
   - Vérifiez que Vault est démarré
   - Vérifiez l'URL et le token
   - Consultez les logs Keycloak

3. **Erreur de connexion DB**
   - Vérifiez les credentials dans Vault
   - Testez la connexion MySQL directement
   - Vérifiez les permissions de l'utilisateur DB

### Commandes de diagnostic
```bash
# Test de santé Vault
curl http://localhost:8200/v1/sys/health

# Vérification du secret
vault kv get secret/ndamli_db_access_dev

# Test de connexion MySQL
mysql -h localhost -u suntel -p cssipres_preprod
```

## 📚 Documentation

- [HashiCorp Vault Documentation](https://www.vaultproject.io/docs)
- [Keycloak User Storage SPI](https://www.keycloak.org/docs/latest/server_development/#_user-storage-spi)
- [BCrypt Specification](https://en.wikipedia.org/wiki/Bcrypt)


