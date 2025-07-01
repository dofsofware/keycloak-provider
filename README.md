# Keycloak Provider avec HashiCorp Vault

Provider Keycloak personnalisé pour IPRES/CSS utilisant HashiCorp Vault pour la gestion sécurisée des credentials de base de données.

## 🎯 Fonctionnalités

- **Authentification externe** : Utilise la table `jhi_user` existante
- **Sécurité renforcée** : Credentials stockés dans HashiCorp Vault
- **Cache intelligent** : Mise en cache des credentials avec TTL de 5 minutes
- **Validation BCrypt** : Support des mots de passe hashés avec BCrypt
- **Gestion des rôles** : Mapping automatique des authorities depuis `jhi_authority`

## 🚀 Installation et Déploiement

### 1. Build du Provider

```bash
# Compiler le projet
mvn clean package

# Le JAR sera généré dans target/ipres_css_provider-1.0.jar
```

### 2. Télécharger les Dépendances

Téléchargez les JARs suivants et placez-les dans le dossier `providers` de Keycloak :

#### HikariCP (Pool de connexions)
```bash
# Télécharger HikariCP-5.0.1.jar
wget https://repo1.maven.org/maven2/com/zaxxer/HikariCP/5.0.1/HikariCP-5.0.1.jar
```
**Lien direct** : [HikariCP-5.0.1.jar](https://repo1.maven.org/maven2/com/zaxxer/HikariCP/5.0.1/HikariCP-5.0.1.jar)

#### jBCrypt (Validation des mots de passe)
```bash
# Télécharger jbcrypt-0.4.jar
wget https://repo1.maven.org/maven2/org/mindrot/jbcrypt/0.4/jbcrypt-0.4.jar
```
**Lien direct** : [jbcrypt-0.4.jar](https://repo1.maven.org/maven2/org/mindrot/jbcrypt/0.4/jbcrypt-0.4.jar)

#### MySQL Connector (Driver JDBC)
```bash
# Télécharger mysql-connector-java-8.0.33.jar
wget https://repo1.maven.org/maven2/mysql/mysql-connector-java/8.0.33/mysql-connector-java-8.0.33.jar
```
**Lien direct** : [mysql-connector-java-8.0.33.jar](https://repo1.maven.org/maven2/mysql/mysql-connector-java/8.0.33/mysql-connector-java-8.0.33.jar)

### 3. Installation dans Keycloak

```bash
# Copier les JARs dans le dossier providers de Keycloak
cp target/ipres_css_provider-1.0.jar $KEYCLOAK_HOME/providers/
cp HikariCP-5.0.1.jar $KEYCLOAK_HOME/providers/
cp jbcrypt-0.4.jar $KEYCLOAK_HOME/providers/
cp mysql-connector-java-8.0.33.jar $KEYCLOAK_HOME/providers/

# Redémarrer Keycloak pour charger les providers
$KEYCLOAK_HOME/bin/kc.sh build
$KEYCLOAK_HOME/bin/kc.sh start-dev
```

### 4. Structure des Dossiers

```
$KEYCLOAK_HOME/
├── providers/
│   ├── ipres_css_provider-1.0.jar          # Notre provider
│   ├── HikariCP-5.0.1.jar                  # Pool de connexions
│   ├── jbcrypt-0.4.jar                     # Validation BCrypt
│   └── mysql-connector-java-8.0.33.jar     # Driver MySQL
└── ...
```

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

### 2. Configuration Keycloak

1. Allez dans **Admin Console → Votre Realm → User Federation**
2. Ajoutez le provider **"ndamli-provider"**
3. Configurez les paramètres :
   - **Vault URL** : `http://localhost:8200`
   - **Vault Token** : `hvs.hWP5WownECWEzuDigz3QRGfZ`
   - **Vault Secret Path** : `secret/data/ndamli_db_access_dev`
   - **Database Driver** : `com.mysql.cj.jdbc.Driver`
4. Cliquez sur **"Test connection"** → devrait être vert ✅
5. Sauvegardez

### 3. Configuration des Rôles dans Keycloak

Pour que les rôles de la table `jhi_authority` apparaissent dans les tokens JWT :

#### Étape 1 : Créer les Rôles dans Keycloak
1. Allez dans **Realm Settings → Roles**
2. Créez les rôles correspondant à vos authorities :
   - `ROLE_ADMIN`
   - `ROLE_CHEF_AGENCE`
   - `ROLE_USER`
   - etc.

#### Étape 2 : Configurer le Mapping des Rôles
1. Allez dans **User Federation → ndamli-provider → Mappers**
2. Cliquez sur **"Create"**
3. Configurez le mapper :
   - **Name** : `authority-role-mapper`
   - **Mapper Type** : `role-ldap-mapper` (ou créer un mapper personnalisé)
   - **Mode** : `READ_ONLY`

#### Étape 3 : Configurer les Client Scopes
1. Allez dans **Client Scopes → roles → Mappers**
2. Vérifiez que le mapper **"realm roles"** est activé
3. Configurez le mapper :
   - **Token Claim Name** : `realm_access.roles`
   - **Add to ID token** : ON
   - **Add to access token** : ON
   - **Add to userinfo** : ON

## 🏗️ Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│    Keycloak     │    │  HashiCorp      │    │    MySQL        │
│                 │    │     Vault       │    │   Database      │
│  ┌───────────┐  │    │                 │    │                 │
│  │ Provider  │──┼────┤  Credentials    │    │   jhi_user      │
│  │           │  │    │   Storage       │    │   jhi_authority │
│  └───────────┘  │    │                 │    │   jhi_user_auth │
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

2. **Rôles manquants dans le token**
   - Vérifiez que les rôles existent dans Keycloak
   - Configurez les mappers de rôles
   - Activez les client scopes appropriés

3. **Erreur de connexion Vault**
   - Vérifiez que Vault est démarré
   - Vérifiez l'URL et le token
   - Consultez les logs Keycloak

4. **Erreur de connexion DB**
   - Vérifiez les credentials dans Vault
   - Testez la connexion MySQL directement
   - Vérifiez les permissions de l'utilisateur DB

5. **JARs manquants**
   - Vérifiez que tous les JARs sont dans le dossier providers
   - Redémarrez Keycloak après ajout des JARs
   - Vérifiez les logs de démarrage Keycloak

### Commandes de diagnostic
```bash
# Test de santé Vault
curl http://localhost:8200/v1/sys/health

# Vérification du secret
vault kv get secret/ndamli_db_access_dev

# Test de connexion MySQL
mysql -h localhost -u suntel -p cssipres_preprod

# Vérification des JARs Keycloak
ls -la $KEYCLOAK_HOME/providers/

# Logs Keycloak
tail -f $KEYCLOAK_HOME/data/log/keycloak.log
```

## 📚 Documentation

- [HashiCorp Vault Documentation](https://www.vaultproject.io/docs)
- [Keycloak User Storage SPI](https://www.keycloak.org/docs/latest/server_development/#_user-storage-spi)
- [BCrypt Specification](https://en.wikipedia.org/wiki/Bcrypt)
- [HikariCP Configuration](https://github.com/brettwooldridge/HikariCP)

## 🎯 Exemple de Token JWT avec Rôles

Après configuration correcte, votre token JWT devrait contenir :

```json
{
  "exp": 1640995200,
  "iat": 1640991600,
  "auth_time": 1640991600,
  "jti": "12345678-1234-1234-1234-123456789012",
  "iss": "http://localhost:8080/realms/your-realm",
  "aud": "your-client",
  "sub": "176",
  "typ": "Bearer",
  "azp": "your-client",
  "session_state": "session-id",
  "realm_access": {
    "roles": [
      "ROLE_ADMIN",
      "ROLE_CHEF_AGENCE"
    ]
  },
  "scope": "openid profile email",
  "email_verified": true,
  "preferred_username": "admin",
  "given_name": "Admin",
  "family_name": "User",
  "email": "admin@example.com"
}
```