# Configuration des Rôles dans Keycloak

Ce guide explique comment configurer les rôles pour que les authorities de la table `jhi_authority` apparaissent dans les tokens JWT.

## 🎯 Objectif

Faire en sorte que les rôles de la base de données (ex: `ROLE_ADMIN`, `ROLE_CHEF_AGENCE`) apparaissent dans le token JWT sous la forme :

```json
{
  "realm_access": {
    "roles": [
      "ROLE_ADMIN",
      "ROLE_CHEF_AGENCE"
    ]
  }
}
```

## 🔧 Configuration Étape par Étape

### Étape 1 : Vérifier les Rôles dans la Base de Données

Assurez-vous que vos rôles sont correctement configurés :

```sql
-- Vérifier les authorities d'un utilisateur
SELECT u.login, a.name as authority_name
FROM jhi_user u
JOIN jhi_user_authority ua ON u.id = ua.user_id
JOIN jhi_authority a ON ua.authority_name = a.name
WHERE u.id = 176;

-- Résultat attendu :
-- login: admin, authority_name: ROLE_ADMIN
-- login: admin, authority_name: ROLE_CHEF_AGENCE
```

### Étape 2 : Créer les Rôles dans Keycloak

1. **Accéder à l'Admin Console Keycloak**
   - Allez dans votre realm → **Realm roles**

2. **Créer les rôles manuellement** (première fois)
   - Cliquez sur **"Create role"**
   - Créez chaque rôle :
     - `ROLE_ADMIN`
     - `ROLE_CHEF_AGENCE`
     - `ROLE_USER`
     - etc.

3. **Ou laisser le provider les créer automatiquement**
   - Le provider créera automatiquement les rôles manquants
   - Vérifiez les logs pour voir les créations automatiques

### Étape 3 : Configurer les Client Scopes

1. **Accéder aux Client Scopes**
   - Allez dans **Client Scopes** → **roles**

2. **Vérifier le Mapper "realm roles"**
   - Cliquez sur l'onglet **Mappers**
   - Vérifiez que le mapper **"realm roles"** existe et est configuré :
     - **Mapper Type** : `User Realm Role`
     - **Token Claim Name** : `realm_access.roles`
     - **Claim JSON Type** : `String`
     - **Add to ID token** : `ON`
     - **Add to access token** : `ON`
     - **Add to userinfo** : `ON`

3. **Si le mapper n'existe pas, le créer** :
   - Cliquez sur **"Create"**
   - **Name** : `realm roles`
   - **Mapper Type** : `User Realm Role`
   - **Token Claim Name** : `realm_access.roles`
   - **Claim JSON Type** : `String`
   - **Add to ID token** : `ON`
   - **Add to access token** : `ON`
   - **Add to userinfo** : `ON`

### Étape 4 : Configurer votre Client

1. **Accéder à votre Client**
   - Allez dans **Clients** → Votre client

2. **Vérifier les Client Scopes**
   - Onglet **Client Scopes**
   - Assurez-vous que **"roles"** est dans **"Assigned Default Client Scopes"**

3. **Configurer les Mappers du Client** (optionnel)
   - Onglet **Mappers**
   - Vous pouvez ajouter des mappers personnalisés si nécessaire

### Étape 5 : Tester la Configuration

1. **Authentifiez-vous avec un utilisateur**
   ```bash
   # Exemple avec curl
   curl -X POST \
     http://localhost:8080/realms/your-realm/protocol/openid-connect/token \
     -H 'Content-Type: application/x-www-form-urlencoded' \
     -d 'grant_type=password' \
     -d 'client_id=your-client' \
     -d 'username=admin' \
     -d 'password=admin'
   ```

2. **Décoder le token JWT**
   - Utilisez [jwt.io](https://jwt.io) pour décoder le token
   - Vérifiez la présence de `realm_access.roles`

## 🔍 Dépannage

### Problème : Rôles manquants dans le token

**Causes possibles :**
1. Les rôles n'existent pas dans Keycloak
2. Le mapper "realm roles" n'est pas configuré
3. Le client scope "roles" n'est pas assigné
4. L'utilisateur n'a pas les rôles assignés

**Solutions :**
1. Vérifiez les logs du provider pour voir si les rôles sont chargés
2. Créez manuellement les rôles dans Keycloak
3. Vérifiez la configuration des mappers
4. Testez avec un utilisateur ayant des rôles connus

### Problème : Erreur "Role not found"

**Solution :**
Le provider crée automatiquement les rôles manquants. Vérifiez les logs :

```
⚠️ Rôle non trouvé dans Keycloak: ROLE_ADMIN - Création automatique
✅ Rôle créé automatiquement: ROLE_ADMIN
```

### Problème : Rôles créés mais pas assignés

**Vérification :**
1. Allez dans **Users** → Votre utilisateur → **Role Mappings**
2. Vérifiez que les rôles sont bien assignés
3. Si non, le problème vient du provider

## 📋 Checklist de Vérification

- [ ] Les authorities existent dans `jhi_user_authority`
- [ ] Le provider charge correctement les authorities (vérifier les logs)
- [ ] Les rôles existent dans Keycloak (Realm roles)
- [ ] Le mapper "realm roles" est configuré
- [ ] Le client scope "roles" est assigné au client
- [ ] L'utilisateur a les rôles assignés dans Keycloak
- [ ] Le token contient `realm_access.roles`

## 🎯 Exemple Complet

Pour un utilisateur avec ID 176 ayant `ROLE_ADMIN` et `ROLE_CHEF_AGENCE` :

1. **Base de données :**
   ```sql
   SELECT * FROM jhi_user_authority WHERE user_id = 176;
   -- Résultat : ROLE_ADMIN, ROLE_CHEF_AGENCE
   ```

2. **Logs du provider :**
   ```
   👥 Utilisateur admin a 2 authorities: ROLE_ADMIN, ROLE_CHEF_AGENCE
   ✅ Rôle trouvé et ajouté: ROLE_ADMIN
   ✅ Rôle trouvé et ajouté: ROLE_CHEF_AGENCE
   ```

3. **Token JWT :**
   ```json
   {
     "realm_access": {
       "roles": [
         "ROLE_ADMIN",
         "ROLE_CHEF_AGENCE"
       ]
     }
   }
   ```

## 🚀 Automatisation

Le provider inclut une création automatique des rôles manquants. Pour désactiver cette fonctionnalité, modifiez le code dans `UserAdapter.getRealmRoleMappingsStream()`.