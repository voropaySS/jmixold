package com.company.competitions;

import java.io.IOException;
import java.lang.String;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import com.company.competitions.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jmix.core.DataManager;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.core.ReferenceToEntitySupport;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.settings.UserSettingsService;
import io.jmix.flowui.xml.layout.loader.component.usermenu.UserMenuItemLoader;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/rest/service")
public class ApiServer {


    private final UserEventListener userEventListener;
    private final UserMenuItemLoader userMenuItemLoader;

    @GetMapping("/sample")
    public String sample() {
        return "Sample";
    }
    private final CurrentAuthentication currentAuthentication;

    public ApiServer(CurrentAuthentication currentAuthentication, DataManager dataManager, FileStorage fileStorage, UserEventListener userEventListener, UserMenuItemLoader userMenuItemLoader) {
        this.currentAuthentication = currentAuthentication;
        this.dataManager = dataManager;
        this.fileStorage = fileStorage;


        this.userEventListener = userEventListener;
        this.userMenuItemLoader = userMenuItemLoader;
    }

    private final DataManager dataManager;

    private final FileStorage fileStorage;


    //при создании документа создается сущность документа и первая версия которая привязывается к документу
    @PostMapping("/upload")
    public Document upload(@RequestPart("file") MultipartFile file,
                           @RequestPart("title") String title) throws IOException {

        UUID currentUserId = ((User) currentAuthentication.getUser()).getId();
        System.out.println(" not easy");
        String filename = file.getOriginalFilename();
        FileRef fileRef = fileStorage.saveStream(filename, file.getInputStream());

        // Создаем новые сущности
        Document document = dataManager.create(Document.class);
        document.setName(title);

        //document.set(fileRef);
        User owner = dataManager.load(User.class).id(currentUserId).one();
        document.setOwner(owner);
        document.setStatus("Черновик");


        dataManager.save(document);
        DocumentVersion newVersionDoc = dataManager.create(DocumentVersion.class);
        newVersionDoc.setFileRef(fileRef);
        newVersionDoc.setNumVersion(1);
        newVersionDoc.setName(filename);
        newVersionDoc.setDocument(document);
        dataManager.save(newVersionDoc);

        System.out.println(document);
        // return new FileResponse(document.getFile().getPath(), filename);
        return document;
    }


    public static class DocumentInput {
        private UUID documentVersionUuid;
        private UUID documentId;
        private UUID soglasovanieId;


        public UUID getDocumentVersionUuid() {
            return documentVersionUuid;
        }

        public void setDocumentVersionUuid(UUID documentVersionUuid) {
            this.documentVersionUuid = documentVersionUuid;
        }

        public UUID getDocumentId() {
            return documentId;
        }

        public void setDocumentId(UUID documentId) {
            this.documentId = documentId;
        }

        public UUID getSoglasovanieId() {
            return soglasovanieId;
        }

        public void setSoglasovanieId(UUID soglasovanieId) {
            this.soglasovanieId = soglasovanieId;
        }

    }
    public static class DocInput {

        private UUID documentId;


        public UUID getDocumentId() {
            return documentId;
        }

        public void setDocumentId(UUID documentId) {
            this.documentId = documentId;
        }


    }


    //это на создание версий уже существующего выбранного документа
    //мы корректируем версию документа и после сохранения создается новая версия
    @PostMapping("/uploadDocVersion")
    public Document uploadDocVersion(@RequestParam UUID documentId, @RequestPart("file") MultipartFile file) throws IOException  {

        UUID documentUuid = documentId;
        //DocumentVersion oldVersion = dataManager.load(DocumentVersion.class).id(docVersionUuid).one();
        Document document = dataManager.load(Document.class).id(documentUuid).one();

        String filename = file.getOriginalFilename();
        FileRef fileRef = fileStorage.saveStream(filename, file.getInputStream());
        System.out.println(" not easy 3");

        List<DocumentVersion> userDocuments = dataManager.load(DocumentVersion.class)
                .query("select e from DocumentVersion e where e.document =:docId order by e.createdDate")
                .parameter("docId", document)
               .list();
// .parameter("user", ((User) currentAuthentication.getUser()))
        // Создаем новые сущности

        DocumentVersion newVersionDoc = dataManager.create(DocumentVersion.class);
        newVersionDoc.setFileRef(fileRef);
        //берем версию последнего созданного
        newVersionDoc.setNumVersion(userDocuments.get(0).getNumVersion()+1);
        newVersionDoc.setName(filename);
        newVersionDoc.setDocument(document);
        dataManager.save(newVersionDoc);

        System.out.println(document);
        // return new FileResponse(document.getFile().getPath(), filename);
        return document;
    }

    @GetMapping("/allDocs")
    public List<Document> allDocs() {
        List<Document> userDocuments = dataManager.load(Document.class)
                .query("select e from Document e where e.owner =:user")
                .parameter("user", ((User) currentAuthentication.getUser())).list();
        System.out.println(userDocuments);
        return userDocuments;
    }

    //или переделать на idшник входной параметр?
    //когда в таблице документа выбираем документ, который уже есть - нужно отобразить его версии для дальнейшего открытия
    @PostMapping("/allVersionDocs")
    public List<DocumentVersion> allVersionDocs(@RequestBody DocInput input) {
        System.out.println(input.getDocumentId());
        List<DocumentVersion> userDocuments = dataManager.load(DocumentVersion.class)
                .query("select e from DocumentVersion e where e.document =:docId")
                .parameter("docId", dataManager.load(Document.class).id(input.getDocumentId()).one())
                .list();
        return userDocuments;
    }

//СОГЛАСОВАНИЕ
    @PostMapping("/createSoglasovanie")
    public Soglasovanie createSoglasovanie(@RequestBody DocumentInput input) {
        Soglasovanie soglasovanie = dataManager.create(Soglasovanie.class);
        soglasovanie.setDocVersion(dataManager.load(DocumentVersion.class).id(input.getDocumentVersionUuid()).one());
        soglasovanie.setStatusSogl("На рассмотрении");
        dataManager.save(soglasovanie);
        return soglasovanie;
    }
    //отдать всех пользователей
    @GetMapping("/allUsers")
    public List<User> allUsers() {
        List<User> users = dataManager.load(User.class)
                .query("select e from User e where e.username <> 'admin'")
                        .list();

        return users;
    }
    //создать пользователя-согласованта
    @PostMapping("/createUserSoglasovant")
    public UserSogl createUserSoglasovant(@RequestBody DocumentInput input) {
        UserSogl userSogl = dataManager.create(UserSogl.class);
        userSogl.setSoglasovanie(dataManager.load(Soglasovanie.class).id(input.getSoglasovanieId()).one());
        userSogl.setStatus("На рассмотрении");
        dataManager.save(userSogl);
        return userSogl;
    }

    //когда пользователь-согласовант нажимает согласовать
    @PostMapping("/toAgree")
    public UserSogl toAgree(@RequestParam UUID userSoglasovantId,@RequestParam UUID soglId) {
        UserSogl userSogl = dataManager.load(UserSogl.class).id(userSoglasovantId).one();
        Soglasovanie sogl = dataManager.load(Soglasovanie.class).id(soglId).one();
        userSogl.setStatus("Согласовано");
        String history = sogl.getHistory();
        history += userSogl.getUser().getLastName() + " " + userSogl.getUser().getLastName() + "согласовал";
        sogl.setHistory(history);
        //здесь надо пересчитать
        dataManager.save(userSogl);
        dataManager.save(sogl);
        return userSogl;
    }
    //когда пользователь согласовант хочет внести комментарий и это вносится в историю согласования
    @PostMapping("/setComment")
    public UserSogl setComment(@RequestParam UUID userSoglasovantId, @RequestParam UUID soglId, @RequestParam String comment) {
        UserSogl userSogl = dataManager.load(UserSogl.class).id(userSoglasovantId).one();
        Soglasovanie sogl = dataManager.load(Soglasovanie.class).id(soglId).one();
        userSogl.setComment(comment);
        String history = sogl.getHistory();
        history += "Комментарий внесен " + userSogl.getComment() + "пользователем" + userSogl.getUser().getLastName();
        sogl.setHistory(history);
        //здесь надо пересчитать
        dataManager.save(userSogl);
        dataManager.save(sogl);
        return userSogl;
    }
    //перед выходом из согласования пересчитывать решения согласовантов
    @PostMapping("/setStatus")
    public Soglasovanie setStatus(@RequestParam UUID soglId) {
        Soglasovanie s = dataManager.load(Soglasovanie.class).id(soglId).one();
        List<UserSogl> userSoglList = dataManager.load(UserSogl.class).query("select e from UserSogl e where" +
                        " e.soglasovanie =:sogl").
                parameter("sogl", s).list();

        Long count = userSoglList.stream().filter(r -> r.getStatus().equals("Согласован") && !r.getStatus().equals("На рассмотрении"))
                .count();
        if (count == userSoglList.stream().count()) {
            s.getDocVersion().getDocument().setStatus("Согласовано");
            dataManager.save(s.getDocVersion().getDocument());
        }
        else {
            s.getDocVersion().getDocument().setStatus("Отклонено");
            dataManager.save(s.getDocVersion().getDocument());
        }

        return s;
    }
    //отфильтровать пользователей согласовантов по вошедшему пользователю
    @GetMapping("/getSogl")
    public List<Soglasovanie> getSogl() {

        List<UserSogl> userSoglList = dataManager.load(UserSogl.class).query("select e from UserSogl e where" +
                        " e.user =: user and " +
                "e.status = 'На рассмотрении").
                parameter("user", ((User) currentAuthentication.getUser())).list();

        return userSoglList.stream().map(e -> e.getSoglasovanie()).toList();
    }
    //прикрепить документ к согласованию, точнее его последнюю версию
    @PostMapping("/attachLastDoc")
    public DocumentVersion attachLastDoc(@RequestBody DocumentInput input) {
        Soglasovanie soglasovanie1 = dataManager.load(Soglasovanie.class).id(input.getSoglasovanieId()).one();
        DocumentVersion last = dataManager.load(DocumentVersion.class).query("select e from DocumentVersion e where e.document =:docId order by e.createDate")
                .parameter("docId", dataManager.load(Document.class).id(input.getDocumentId()))
                .list().get(0);
        soglasovanie1.setDocVersion(last);
        dataManager.save(soglasovanie1);
        return last;
    }

}


