Аутентификация
Команда: /auth
Формат: /auth login password
Пример:
/auth admin admin
/auth login1 password1

Регистрация
Команда: /register
Формат: /register login nick password
Пример:
/register login1 nick1 password1

Отправка сообщения конкретному пользователю
Команда: /w
Формат: /w user message
Пример:
/w tom Hello tom

Отправка сообщения всем
Команда: /w
Формат: /w message
Пример:
/w Hello friend


20 минут = 1 200 секунд
20 минут = 1 200 000 миллисекунд



sendMessage
Запомнить время отправки
Через какое то время проверять текущее и время отправки
currentTime - sendMessageLastTime >= 20 минут
если true, тогда поток interrupt
в противном случае работаем

а выше где-то мы должны делать так и обрабатывать сообщение
while(!Thread.currentThread().isInterrupted())



________________________________________________________________________
Возможность банить клиентов,
баны могут быть по времени или перманентные (так может делать админ админ)

/ban nickname isPermanent countMinutes

/ban nickname true
/ban nickname false 60


________________________________________________________________________


1) Админ забанил пользователя
2) В таблицу user добавляется информация по бану пользователя
3) Список user в DatabaseAuthenticationProvider должен обновиться
4)


/auth admin admin

/register login1 nick1 password1
/auth login1 password1

/register login2 nick2 password2
/auth login2 password2

/ban nick2 30

// если /ban nick2 , бан будет перманентный
// если /ban nick2 3 , бан будет временный

/w hello all
hi all
/list


            // /exit -> disconnect()
            // /w user message -> user
            // /w tom Hello tom