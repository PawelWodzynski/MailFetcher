// Przełącz na bazę mailreader (lub utwórz ją, jeśli nie istnieje)
db = db.getSiblingDB('mailreader');
// Usuń istniejące kolekcje
db.emails.drop(); db.files.drop();
// Utwórz kolekcję dla emaili
db.createCollection("emails");
// Utwórz kolekcję dla plików załączników
db.createCollection("files");
// Indeksy dla kolekcji emails
db.emails.createIndex({ "senderEmail": 1 }); db.emails.createIndex({ "title": 1 }); 
db.emails.createIndex({ "recipientEmail": 1 }); db.emails.createIndex({ "sentDate": 1 }); 
db.emails.createIndex({ "messageId": 1 }, { unique: true, sparse: true });
// Złożony indeks do wykrywania duplikatów bez Message-ID
db.emails.createIndex({ "senderEmail": 1, "title": 1, "sentDate": 1
});
// Indeksy dla kolekcji files
db.files.createIndex({ "emailId": 1 }); db.files.createIndex({ "emailId": 1, "fileName": 1
}, { unique: true });
// Wstaw przykładowy email
let emailId = new ObjectId(); db.emails.insertOne({ _id: emailId, title: "Test Email", 
    senderEmail: "sender@example.com", recipientEmail: "recipient@example.com", content: "This is 
    a test email content", sentDate: new Date(), receivedDate: new Date(), messageId: 
    "<test.12345@example.com>", fileIds: [] // Lista ID załączników
});
// Wstaw przykładowy plik załącznika
let fileId = new ObjectId(); db.files.insertOne({ _id: fileId, emailId: emailId.toString(), 
    fileName: "test-attachment.pdf", contentType: "application/pdf", size: 12345, fileData: new 
    Binary(Buffer.from("Test binary data"))
});
// Zaktualizuj email, dodając odniesienie do załącznika
db.emails.updateOne( { _id: emailId }, { $push: { fileIds: fileId.toString() } } );
print("Inicjalizacja bazy danych zakończona pomyślnie!");
